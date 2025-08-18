
#Get project root directory based on autogen.sh file location
SCRT_DIR=$(cd $(dirname "${BASH_SOURCE[0]}") && pwd)
if [[ $SCRT_DIR = *" "* ]]; then #This is an autoconf workaround to support whitespace in $PATH
  echo "ERROR: Whitespace found in current working directory."$'\n'"please move the project to a location without whitespace in its path."
  exit 1
fi
SUBPROJECT_DIR=$SCRT_DIR/subprojects

#Cache folder for downloaded sources
SRC_CACHE_DIR=$SUBPROJECT_DIR/.cache

NO_DOWNLOAD=0
FAILED=0

GSTREAMER_ANDROID_LIBS=gstreamer-1.0-android-universal-1.26.5

#WGET_CMD="wget --force-progress" #Since version 2+ show-progress ins't supported
WGET_CMD="wget --show-progress --progress=bar:force" #Since version 1.16 set show-progress

GSTREAMER_ROOT_ANDROID="${GSTREAMER_ROOT_ANDROID#\"}"  # Removes leading "
GSTREAMER_ROOT_ANDROID="${GSTREAMER_ROOT_ANDROID%\"}" # Removes trailing "

og_tool_chain=$TOOLCHAIN
og_target=$TARGET
og_api=$API
og_ar=$AR
og_as=$AS
og_cc=$CC
og_cxx=$CXX
og_ld=$LD
og_ranlib=$RANLIB
og_strip=$STRIP
og_chost=$CHOST

TARGET_TOOLCHAIN=$CMAKE_ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64
TARGET_TARGET=x86_64-linux-android
TARGET_API=$API
TARGET_AR=$TARGET_TOOLCHAIN/bin/llvm-ar
TARGET_CC="$CMAKE_C_COMPILER --target=${TARGET_TARGET}${TARGET_API}"
TARGET_AS=$CC
TARGET_CXX="$CMAKE_CXX_COMPILER --target=${TARGET_TARGET}${TARGET_API}"
TARGET_LD=$TARGET_TOOLCHAIN/bin/ld.lld
TARGET_RANLIB=$TARGET_TOOLCHAIN/bin/llvm-ranlib
TARGET_STRIP=$TARGET_TOOLCHAIN/bin/llvm-strip
TARGET_CHOST="arm-linux-androideabi"

mkdir -p "$SUBPROJECT_DIR"
mkdir -p "$SRC_CACHE_DIR"

unbuffer echo "test" 2> /dev/null 1> /dev/null
[ $? == 0 ] && UNBUFFER_COMMAND="unbuffer" && echo "unbuffer found" || echo "unbuffer not found"

unbufferCall(){
  if [ ! -z "$UNBUFFER_COMMAND" ]; then
    eval "unbuffer ${@}"
  else
    script -efq -c "$(printf "%s" "${@}")"
  fi
}

############################################
#
# Function to print time for human
#
############################################
function displaytime {
  local time msg label
  local "${@}"
  local T=${time}
  local D=$((T/60/60/24))
  local H=$((T/60/60%24))
  local M=$((T/60%60))
  local S=$((T%60))
  [[ ! -z "${msg}" ]] && output="${msg}"$'\n'$'\n'$label || output="$label"
  (( $D > 0 )) && output=$output"$D days "
  (( $H > 0 )) && output=$output"$H hours "
  (( $M > 0 )) && output=$output"$M minutes "
  (( $D > 0 || $H > 0 || $M > 0 )) && output=$output"and "
  echo "${output}${S} seconds"
}

############################################
#
# Function to download and extract tar package file
# Can optionally use a caching folder defined with SRC_CACHE_DIR
#
############################################
downloadAndExtract (){
  local path file forcedownload noexit out # reset first
  local "${@}"

  if [ -z "${forcedownload}" ] && [ $NO_DOWNLOAD -eq 1 ]; then
    echo "ERROR: download disabled. Missing dependency $file"
    [[ ! -z "${noexit}" ]] && FAILED=1 && return || exit 1;
  fi

  [ ! -z "$SRC_CACHE_DIR" ] && pathprefix="$SRC_CACHE_DIR/" || pathprefix=""

  dest_val=${pathprefix}${file}
    echo "cheeck file $dest_val"
    echo "path file $path"

  if [ ! -f "$dest_val" ]; then
    echo "downloading: ${path}"
    $WGET_CMD "${path}" -O "${dest_val}"
    if [ "${PIPESTATUS[0]}" -ne 0 ]; then
      echo "ERROR: failed to fetch ${path}"
      [[ ! -z "${noexit}" ]] && FAILED=1 && return || exit 1;
    fi
  else
    echo "source already downloaded"
  fi

  filename="${file%.*}"
  if [[ "${dest_val}" == *.tar.gz ]]; then
    filename="${filename%.*}"
    [ -z "${out}" ] && output_dir="${SUBPROJECT_DIR}/${filename}" || output_dir="${out}"
    mkdir -p "${output_dir}"
    tar xfz "${dest_val}" -C "${output_dir}"
  elif [[ "${dest_val}" == *.tar.xz ]]; then
    filename="${filename%.*}"
    [ -z "${out}" ] && output_dir="${SUBPROJECT_DIR}/${filename}" || output_dir="${out}"
    mkdir -p "${output_dir}"
    tar xf "${dest_val}" -C "${output_dir}/"
  elif [[ "${dest_val}" == *.tar.bz2 ]]; then
    filename="${filename%.*}"
    [ -z "${out}" ] && output_dir="${SUBPROJECT_DIR}/${filename}" || output_dir="${out}"
    mkdir -p "${output_dir}"
    tar xjf "${dest_val}" -C "${output_dir}/"
  elif [[ "${dest_val}" == *.zip ]]; then
    checkUnzip
    if [ -z "${out}" ]; then
      output_dir="${SUBPROJECT_DIR}/${filename}"
    else
      output_dir="${out}"
    fi
    mkdir -p "${output_dir}"
    unzip -oq "${dest_val}" -d "${output_dir}/"
  else
    echo "ERROR: downloaded file not found. ${file}"
    [[ ! -z "${noexit}" ]] && FAILED=1 && return || exit 1;
  fi
  if [ "${PIPESTATUS[0]}" -ne 0 ]; then
    echo "ERROR: failed to extract ${file}"
    [[ ! -z "${noexit}" ]] && FAILED=1 && return || exit 1;
  fi
}


############################################
#
# Function to clone a git repository or pull if it already exists locally
# Can optionally use a caching folder defined with SRC_CACHE_DIR
#
############################################
privatepullOrClone(){
  local path dest tag recurse depth # reset first
  local "${@}"

  tgstr=""
  tgstr2=""
  if [ ! -z "${tag}" ]
  then
    tgstr="origin tags/${tag}"
    tgstr2="-b ${tag}"
  fi

  recursestr=""
  if [ ! -z "${recurse}" ]
  then
    recursestr="--recurse-submodules"
  fi
  depthstr=""
  if [ ! -z "${depth}" ]
  then
    depthstr="--depth ${depth}"
  fi

  should_clone=1
  if [ -d ${dest} ]; then
    unbufferCall "git -C ${dest} pull ${tgstr}"
    should_clone=${PIPESTATUS[0]}
    if [ $should_clone -ne 0 ]; then #Something failed. Remove to redownload
      rm -rf ${dest}
    fi
  fi

  if [ $should_clone -ne 0 ]; then
    unbufferCall "git clone -j$(nproc) $recursestr $depthstr $tgstr2 ${path} $dest"
    if [ "${PIPESTATUS[0]}" -ne 0 ]; then
      echo "ERROR: failed to fetch ${path}"
      [[ ! -z "${noexit}" ]] && FAILED=1 && return || exit 1;
    fi
  fi
}

pullOrClone (){
  local path tag depth recurse ignorecache forcedownload noexit # reset first
  local "${@}"

  if [ -z "${forcedownload}" ] && [ $NO_DOWNLOAD -eq 1 ]; then
    echo "ERROR: Download disabled. Missing dependency $path"
    [[ ! -z "${noexit}" ]] && FAILED=1 && return || exit 1
  fi

  echo "clone ${tag}@${path}"
  IFS='/' read -ra ADDR <<< "$path"
  namedotgit=${ADDR[-1]}
  IFS='.' read -ra ADDR <<< "$namedotgit"
  name=${ADDR[0]}

  dest_val=""
  if [ ! -z "$SRC_CACHE_DIR" ] && [ -z "${ignorecache}" ]; then
    dest_val="$SRC_CACHE_DIR/$name"
  else
    dest_val=$name
  fi
  if [ ! -z "${tag}" ]; then
    dest_val+="-${tag}"
  fi

  if [ -z "$SRC_CACHE_DIR" ] || [ -z "${ignorecache}" ]; then
    #TODO Check if it's the right tag
    privatepullOrClone dest=$dest_val tag=$tag depth=$depth recurse=$recurse path=$path
  elif [ -d "$dest_val" ] && [ ! -z "${tag}" ]; then #Folder exist, switch to tag
    currenttag=$(git -C $dest_val tag --points-at ${tag})
    echo "TODO Check current tag \"${tag}\" == \"$currenttag\""
    privatepullOrClone dest=$dest_val tag=$tag depth=$depth recurse=$recurse path=$path
  elif [ -d "$dest_val" ] && [ -z "${tag}" ]; then #Folder exist, switch to main
    currenttag=$(git -C $dest_val tag --points-at ${tag})
    echo "TODO Handle no tag \"${tag}\" == \"$currenttag\""
    privatepullOrClone dest=$dest_val tag=$tag depth=$depth recurse=$recurse path=$path
  elif [ ! -d "$dest_val" ]; then #fresh start
    privatepullOrClone dest=$dest_val tag=$tag depth=$depth recurse=$recurse path=$path
  else
    if [ -d "$dest_val" ]; then
      echo "yey destval"
    fi
    if [ -z "${tag}" ]; then
      echo "yey tag"
    fi
    echo "1 $dest_val : $(test -f \"$dest_val\")"
    echo "2 ${tag} : $(test -z \"${tag}\")"
  fi

  if [ $FAILED -eq 0 ] && [ ! -z "$SRC_CACHE_DIR" ] && [ -z "${ignorecache}" ]; then
    echo "copy repo from cache"$"$dest_val"
    rm -rf "$name"
    cp -r "$dest_val" "./$name"
  fi
}

setTargetBuildVariables(){
  export TOOLCHAIN=$TARGET_TOOLCHAIN
  export TARGET=$TARGET_TARGET
  export API=$TARGET_API #minSdk version
  export AR=$TARGET_AR
  export CC=$TARGET_CC
  export AS=$TARGET_AS
  export CXX=$TARGET_CXX
  export LD=$TARGET_LD
  export RANLIB=$TARGET_RANLIB
  export STRIP=$TARGET_STRIP
  export CHOST=$TARGET_CHOST

  echo "TOOLCHAIN=$TOOLCHAIN"
  echo "TARGET=$TARGET"
  echo "API=$API"
  echo "AR=$AR"
  echo "CC=$CC"
  echo "CXX=$CXX"
  echo "AS=$AS"
  echo "LD=$LD"
  echo "RANLIB=$RANLIB"
  echo "STRIP=$STRIP"
  echo "CHOST=$CHOST"
}

setTargetBuildVariables

if [ $FAILED -eq 1 ]; then
  echo "Script encountered one of more failures"
fi

# Soft check if library archive is already extracted
# TODO hard check for all library needed
if [ ! -f "${GSTREAMER_ROOT_ANDROID}/x86/lib/libglib-2.0.a" ]; then
  downloadAndExtract file="${GSTREAMER_ANDROID_LIBS}.tar.xz" out="${GSTREAMER_ROOT_ANDROID}" path="https://gstreamer.freedesktop.org/data/pkg/android/1.26.5/${GSTREAMER_ANDROID_LIBS}.tar.xz"
else
  echo "Folder already extracted."
fi

echo "autogen.sh completed successfully"