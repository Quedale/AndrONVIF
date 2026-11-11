
#Get project root directory based on autogen.sh file location
ENABLE_DEBUG=0
SCRT_DIR=$(cd $(dirname "${BASH_SOURCE[0]}") && pwd)

export CFLAGS="-fPIC -O2 -g "$CFLAGS
export CXXFLAGS="-fPIC -O2 -g"$CXXFLAGS

SUBPROJECTS_ROOT="${SUBPROJECTS_ROOT:=${SCRT_DIR}/subprojects}"

#Cache folder for downloaded sources
SRC_CACHE_DIR=$SUBPROJECTS_ROOT/.cache

NO_DOWNLOAD=0
FAILED=0

GSTREAMER_VERSION=1.29.2
GSTREAMER_ANDROID_LIBS=gstreamer-1.0-android-universal-$GSTREAMER_VERSION

#WGET_CMD="wget --force-progress" #Since version 2+ show-progress ins't supported
WGET_CMD="wget --show-progress --progress=bar:force" #Since version 1.16 set show-progress

#GSTREAMER_ROOT_ANDROID="${GSTREAMER_ROOT_ANDROID#\"}"  # Removes leading "
#GSTREAMER_ROOT_ANDROID="${GSTREAMER_ROOT_ANDROID%\"}" # Removes trailing "

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

if [[ $SCRT_DIR = *" "* ]]; then #This is an autoconf workaround to support whitespace in $PATH
  echo "ERROR: Whitespace found in current working directory."$'\n'"please move the project to a location without whitespace in its path."
  exit 1
fi

mkdir -p "$SUBPROJECTS_ROOT"
mkdir -p "$SRC_CACHE_DIR"

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
  echo "${output}${S} seconds" >&2
}

progVersionCheck(){
  local major minor micro linenumber lineindex program paramoverride startcut length
  local "${@}"

  varparam="--version"
  if [ ! -z ${paramoverride} ]; then
    varparam="${paramoverride}"
  fi;
  # echo "------------------------" 1> /dev/stdin
  # echo "test ${program} ${varparam}" 1> /dev/stdin
  # echo "------------------------" 1> /dev/stdin

  test=$(${program} $varparam 2> /dev/null)
  ret=$?
  if [[ $ret -ne 0 ]]; then
      echo "prog ${program} not found" >&2
      echo 1
      return
  fi

  FIRSTLINE=$(${program} ${varparam} | head -${linenumber})
  firstLineArray=(${FIRSTLINE//;/ })
  versionString=${firstLineArray[${lineindex}]};

  [[ ! -z "${startcut}" ]] && [[ -z "${length}" ]] && versionString=${versionString:2}
  [[ ! -z "${startcut}" ]] && [[ ! -z "${length}" ]] && versionString=${versionString:$startcut:$length}
  [[ -z "${startcut}" ]] && [[ ! -z "${length}" ]] && versionString=${versionString:0:$length}

  versionArray=(${versionString//./ })
  actualmajor=${versionArray[0]}
  actualminor=${versionArray[1]}
  actualmicro=${versionArray[2]}

  if [ -z "$major"  ]; then
    echo "prog ${program} found" >&2
    return;
  fi

  if [[ -z "$actualmajor"  || ! "$actualmajor" == ?(-)+([[:digit:]]) ]]; then echo "Unexpected ${program} version ouput[1] \"$FIRSTLINE\" \"$versionString\""; return; fi
  if [ "$actualmajor" -gt "$major" ]; then
    echo "prog ${program} found" >&2
    return;
  elif [ "$actualmajor" -lt "$major" ]; then
    echo "prog ${program} version is too old. $actualmajor.$actualminor.$actualmicro < $major.$minor.$micro" >&2
    echo 1
  else
    if [ -z "$minor"  ]; then return; fi
    if [[ -z "$actualminor"  || ! "$actualminor" == ?(-)+([[:digit:]]) ]]; then echo "Unexpected ${program} version ouput[2] \"$FIRSTLINE\" \"$versionString\""; return; fi
    if [ "$actualminor" -gt "$minor" ]; then
      echo "prog ${program} found" >&2
      return;
    elif [ "$actualminor" -lt "$minor" ]; then
      echo "prog ${program} version is too old. $actualmajor.$actualminor.$actualmicro < $major.$minor.$micro" >&2
      echo 1
    else
      if [ -z "$micro"  ]; then return; fi
      if [[ -z "$actualmicro"  || ! "$actualmicro" == ?(-)+([[:digit:]]) ]]; then echo "Unexpected ${program} version ouput[3] \"$FIRSTLINE\" \"$versionString\""; return; fi
      if [ ! -z "$micro" ] && [ "$actualmicro" -lt "$micro" ]; then
        echo "prog ${program} version is too old. $actualmajor.$actualminor.$actualmicro < $major.$minor.$micro" >&2
        echo 1
      else
        echo "prog ${program} found" >&2
        return
      fi
    fi
  fi
}


export PATH="$SUBPROJECT_DIR/unzip60/build/dist/bin":$PATH
HAS_UNZIP=0
checkUnzip(){
  if [ $HAS_UNZIP -eq 0 ] && [ ! -z "$(progVersionCheck program=unzip paramoverride="-v")" ]; then
    downloadAndExtract project="unzip" file="unzip60.tar.gz" path="https://sourceforge.net/projects/infozip/files/UnZip%206.x%20%28latest%29/UnZip%206.0/unzip60.tar.gz/download"
    buildMakeProject project="unzip" srcdir="unzip60" make="-f unix/Makefile generic" installargs=" prefix=$SUBPROJECT_DIR/unzip60/build/dist MANDIR=$SUBPROJECT_DIR/unzip60/build/dist/share/man/man1 -f unix/Makefile" localtoolchain="true" noabi=true
  fi
  HAS_UNZIP=1
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
    echo "ERROR: download disabled. Missing dependency $file" >&2
    [[ ! -z "${noexit}" ]] && FAILED=1 && return || exit 1;
  fi

  [ ! -z "$SRC_CACHE_DIR" ] && pathprefix="$SRC_CACHE_DIR/" || pathprefix=""

  dest_val=${pathprefix}${file}
    echo "cheeck file $dest_val" >&2
    echo "path file $path" >&2

  if [ ! -f "$dest_val" ]; then
    echo "downloading: ${path}" >&2
    $WGET_CMD "${path}" -O "${dest_val}"
    if [ "${PIPESTATUS[0]}" -ne 0 ]; then
      echo "ERROR: failed to fetch ${path}" >&2
      [[ ! -z "${noexit}" ]] && FAILED=1 && return || exit 1;
    fi
  else
    echo "source already downloaded" >&2
  fi

  filename="${file%.*}"
  if [[ "${dest_val}" == *.tar.gz ]]; then
    filename="${filename%.*}"
    [ -z "${out}" ] && output_dir="${SUBPROJECTS_ROOT}/${filename}" || output_dir="${out}"
    mkdir -p "${output_dir}"
    tar xfz "${dest_val}" -C "${output_dir}"
  elif [[ "${dest_val}" == *.tar.xz ]]; then
    filename="${filename%.*}"
    [ -z "${out}" ] && output_dir="${SUBPROJECTS_ROOT}/${filename}" || output_dir="${out}"
    mkdir -p "${output_dir}"
    tar xf "${dest_val}" -C "${output_dir}/"
  elif [[ "${dest_val}" == *.tar.bz2 ]]; then
    filename="${filename%.*}"
    [ -z "${out}" ] && output_dir="${SUBPROJECTS_ROOT}/${filename}" || output_dir="${out}"
    mkdir -p "${output_dir}"
    tar xjf "${dest_val}" -C "${output_dir}/"
  elif [[ "${dest_val}" == *.zip ]]; then
    checkUnzip
    if [ -z "${out}" ]; then
      output_dir="${SUBPROJECTS_ROOT}/${filename}"
    else
      output_dir="${out}"
    fi
    mkdir -p "${output_dir}"
    unzip -oq "${dest_val}" -d "${output_dir}/"
  else
    echo "ERROR: downloaded file not found. ${file}" >&2
    [[ ! -z "${noexit}" ]] && FAILED=1 && return || exit 1;
  fi
  if [ "${PIPESTATUS[0]}" -ne 0 ]; then
    echo "ERROR: failed to extract ${file}" >&2
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
    git -C ${dest} pull ${tgstr}
    should_clone=${PIPESTATUS[0]}
    if [ $should_clone -ne 0 ]; then #Something failed. Remove to redownload
      rm -rf ${dest}
    fi
  fi

  if [ $should_clone -ne 0 ]; then
    git clone -j$(nproc) $recursestr $depthstr $tgstr2 ${path} $dest
    if [ "${PIPESTATUS[0]}" -ne 0 ]; then
      echo "ERROR: failed to fetch ${path}" >&2
      [[ ! -z "${noexit}" ]] && FAILED=1 && return || exit 1;
    fi
  fi
}

pullOrClone (){
  local path tag depth recurse ignorecache forcedownload noexit # reset first
  local "${@}"

  if [ -z "${forcedownload}" ] && [ $NO_DOWNLOAD -eq 1 ]; then
    echo "ERROR: Download disabled. Missing dependency $path" >&2
    [[ ! -z "${noexit}" ]] && FAILED=1 && return || exit 1
  fi

  echo "clone ${tag}@${path}" >&2
  IFS='/' read -ra ADDR <<< "$path"
  namedotgit=${ADDR[-1]}
  IFS='.' read -ra ADDR <<< "$namedotgit"
  name=${ADDR[0]}

  dest_val=""
  if [ ! -z "$SRC_CACHE_DIR" ] && [ -z "${ignorecache}" ]; then
    dest_val="$SRC_CACHE_DIR/$name"
  else
    dest_val="${SUBPROJECTS_ROOT}/${name}"
  fi
  if [ ! -z "${tag}" ]; then
    dest_val+="-${tag}"
  fi

  if [ -z "$SRC_CACHE_DIR" ] || [ -z "${ignorecache}" ]; then
    #TODO Check if it's the right tag
    privatepullOrClone dest=$dest_val tag=$tag depth=$depth recurse=$recurse path=$path
  elif [ -d "$dest_val" ] && [ ! -z "${tag}" ]; then #Folder exist, switch to tag
    currenttag=$(git -C $dest_val tag --points-at ${tag})
    echo "TODO Check current tag \"${tag}\" == \"$currenttag\"" >&2
    privatepullOrClone dest=$dest_val tag=$tag depth=$depth recurse=$recurse path=$path
  elif [ -d "$dest_val" ] && [ -z "${tag}" ]; then #Folder exist, switch to main
    currenttag=$(git -C $dest_val tag --points-at ${tag})
    echo "TODO Handle no tag \"${tag}\" == \"$currenttag\"" >&2
    privatepullOrClone dest=$dest_val tag=$tag depth=$depth recurse=$recurse path=$path
  elif [ ! -d "$dest_val" ]; then #fresh start
    privatepullOrClone dest=$dest_val tag=$tag depth=$depth recurse=$recurse path=$path
  else
    if [ -d "$dest_val" ]; then
      echo "yey destval" >&2
    fi
    if [ -z "${tag}" ]; then
      echo "yey tag" >&2
    fi
    echo "1 $dest_val : $(test -f \"$dest_val\")" >&2
    echo "2 ${tag} : $(test -z \"${tag}\")" >&2
  fi

  if [ $FAILED -eq 0 ] && [ ! -z "$SRC_CACHE_DIR" ] && [ -z "${ignorecache}" ]; then
    echo "copy repo from cache"$"$dest_val" >&2
    rm -rf "$name"
    cp -r "$dest_val" "./$name"
  fi
}

############################################
#
# Function to build a project configured with autotools
#
############################################
buildMakeProject(){
  local project srcdir prefix autogen autoreconf configure make cmakedir cmakeargs cmakeclean installargs skipbootstrap bootstrap configcustom outoftree noexit localtoolchain noabi
  local "${@}"

  build_start=$SECONDS

  echo "${project}: src: '${srcdir}'" >&2
  echo "${project}: prefix: '${prefix}'" >&2

  if [ ! -z "${localtoolchain}" ]; then
    echo "Using local toolkit" >&2
    export TOOLCHAIN=$og_tool_chain
    export TARGET=$og_target
    export API=$og_api
    export AR=$og_ar
    export AS=$og_as
    export CC="" #This variable is always set by gradle/androidstudio(?) forcing system default
    export CXX="" #This variable is always set by gradle/androidstudio(?) forcing system default
    export LD=$og_ld
    export RANLIB=$og_ranlib
    export STRIP=$og_strip
    export CHOST=$og_chost
  fi

  if [ "${outoftree}" == "true" ]; then
    mkdir -p "${SUBPROJECTS_ROOT}/${srcdir}/build"
    cd "${SUBPROJECTS_ROOT}/${srcdir}/build"
    rel_path=".."
  else
    cd "${SUBPROJECTS_ROOT}/${srcdir}/"
    rel_path="."
  fi

  if [ -f "$rel_path/bootstrap" ] && [ -z "${skipbootstrap}" ]; then
    echo "${project}: src: ${srcdir}" >&2
    $rel_path/bootstrap ${bootstrap} >&2
    if [ "${PIPESTATUS[0]}" -ne 0 ]; then
      echo "${project}: ERROR: $rel_path/bootstrap failed ${srcdir}" >&2
      [[ ! -z "${noexit}" ]] && FAILED=1 && cd "$OLDPWD" && return || exit 1;
    fi
  fi

  if [ -f "$rel_path/bootstrap.sh" ]; then
    echo "${project}: src: ${srcdir}" >&2
    $rel_path/bootstrap.sh ${bootstrap} >&2
    if [ "${PIPESTATUS[0]}" -ne 0 ]; then
      echo "${project}: ERROR: $rel_path/bootstrap.sh failed ${srcdir}" >&2
      [[ ! -z "${noexit}" ]] && FAILED=1 && cd "$OLDPWD" && return || exit 1;
    fi
  fi
  if [ -f "$rel_path/autogen.sh" ] && [ "${autogen}" != "skip" ]; then
    echo "${project}: src: ${srcdir}" >&2
    $rel_path/autogen.sh ${autogen} >&2
    if [ "${PIPESTATUS[0]}" -ne 0 ]; then
      echo "${project}: ERROR: Autogen failed ${srcdir}" >&2
      [[ ! -z "${noexit}" ]] && FAILED=1 && cd "$OLDPWD" && return || exit 1;
    fi
  fi

  if [ ! -z "${noabi}" ]; then
    innerprefix=${prefix}
  else
    innerprefix=${prefix}/${ANDROID_ABI}
  fi
  echo "${project}: corrected prefix: '${innerprefix}'" >&2

  if [ ! -z "${autoreconf}" ]
  then
    echo "${project}: src: ${srcdir}" >&2
    currdir=$(pwd)
    cd "${rel_path}"
    autoreconf ${autoreconf} >&2
    cd "${currdir}"
    if [ "${PIPESTATUS[0]}" -ne 0 ]; then
      echo "${project}: ERROR: Autoreconf failed ${srcdir}" >&2
      [[ ! -z "${noexit}" ]] && FAILED=1 && cd "$OLDPWD" && return || exit 1;
    fi
  fi

  if [ ! -z "${cmakedir}" ]; then
    echo "${project}: src: ${srcdir}" >&2
    echo "${project}: arguments: ${cmakeargs}" >&2
    if [ ! -z "${cmakeclean}" ]; then
      cmake --build "${cmakedir}" --target clean >&2
      find . -iwholename '*cmake*' -not -name CMakeLists.txt -delete
    fi

    btype="Release"
    if [ $ENABLE_DEBUG -eq 1 ]; then
      btype="Debug"
    fi

    cmake -G 'Unix Makefiles' \
      -DCMAKE_TOOLCHAIN_FILE=$CMAKE_TOOLCHAIN_FILE \
      -DANDROID_ABI=$ANDROID_ABI \
      ${cmakeargs} \
      -DCMAKE_BUILD_TYPE=$btype \
      -DCMAKE_INSTALL_PREFIX="${innerprefix}" \
      "$(pwd)/${cmakedir}" >&2

    if [ "${PIPESTATUS[0]}" -ne 0 ]; then
      echo "${project}: ERROR cmake failed ${srcdir}" >&2
      [[ ! -z "${noexit}" ]] && FAILED=1 && cd "$OLDPWD" && return || exit 1;
    fi
  fi

  if [ ! -z "${configcustom}" ]; then
    echo "${project}: src: ${srcdir}" >&2
    echo "${project}: command: ${configcustom}" >&2
    bash -c "${configcustom}" >&2
    if [ "${PIPESTATUS[0]}" -ne 0 ]; then
      echo "${project}: ERROR: command failed: ${configcustom}" >&2
      [[ ! -z "${noexit}" ]] && FAILED=1 && cd "$OLDPWD" && return || exit 1;
    fi
  fi

  if [ -f "${SUBPROJECTS_ROOT}/${srcdir}/configure" ] && [ -z "${cmakedir}" ]; then
    echo "${project}: src: ${srcdir}" >&2
    echo "${project}: arguments: ${configure}" >&2
#    exit 1
    ${rel_path}/configure  \
        --prefix="${innerprefix}"  \
        ${configure} >&2
    if [ "${PIPESTATUS[0]}" -ne 0 ]; then
      echo "${project}: ERROR: $rel_path/configure failed ${srcdir}" >&2
      [[ ! -z "${noexit}" ]] && FAILED=1 && cd "$OLDPWD" && return || exit 1;
    fi
  else
    echo "${project}: no configuration available" >&2
  fi

  echo "${project}: src: ${srcdir}" >&2
  echo "${project}: arguments: '${make}'" >&2
  make -C $(pwd) -j$(nproc) ${make} >&2
  if [ "${PIPESTATUS[0]}" -ne 0 ]; then
    echo "${project}: ERROR: make failed ${srcdir}" >&2
    [[ ! -z "${noexit}" ]] && FAILED=1 && cd "$OLDPWD" && return || exit 1;
  fi

  echo "${project}: src: ${srcdir}" >&2
  echo "${project}: arguments: ${installargs}" >&2
  make ${SUBPROJECTS_ROOT}/${srcdir} -j$(nproc) ${make} install ${installargs} >&2
  if [ "${PIPESTATUS[0]}" -ne 0 ]; then
    echo "${project}: ERROR: make install failed ${srcdir}" >&2
    [[ ! -z "${noexit}" ]] && FAILED=1 && cd "$OLDPWD" && return || exit 1;
  fi

  if [ ! -z "${distclean}" ]; then
    echo "${project}:Cleaning build artifacts" >&2
    make -C $(pwd) -j$(nproc) distclean >&2
  fi

  build_time=$(( SECONDS - build_start ))
  displaytime project="${project}" task="build" time=$build_time label="Build runtime: "

  if [ -z "${localtoolchain}" ]; then
    setTargetBuildVariables
  fi

  cd "$OLDPWD"
}

pkgCheck() {
  local name minver
  local "${@}"

  min_ver=""
  if [ ! -z "${minver}" ]; then
    min_ver=" >= ${minver}"
  fi
  output=$(pkg-config --print-errors --errors-to-stdout "${name} $min_ver");
  if [ -z "$output" ]; then
    echo "${name} found" >&2
  else
    echo "pkg ${name} not found" >&2
    echo 1
  fi
}

checkGitRepoState(){
  local repo package
  local "${@}"

  ret=0
  git -C ${SUBPROJECTS_ROOT}/${repo} remote update
  LOCAL=$(git -C ${SUBPROJECTS_ROOT}/${repo} rev-parse @ 2> /dev/null)
  REMOTE=$(git -C ${SUBPROJECTS_ROOT}/${repo} rev-parse @{u} 2> /dev/null)
  BASE=$(git -C ${SUBPROJECTS_ROOT}/${repo} merge-base @ @{u} 2> /dev/null)

  if [ ! -z "$LOCAL" ] && [ $LOCAL = $REMOTE ]; then
    echo "${repo} is already up-to-date. Do nothing..." >&2
  elif [ ! -z "$LOCAL" ] && [ $LOCAL = $BASE ]; then
    echo "${repo} has new changes. Force rebuild..." >&2
    ret=1
  elif [ ! -z "$LOCAL" ] && [ $REMOTE = $BASE ]; then
    echo "${repo} has local changes. Doing nothing..." >&2
  elif [ ! -z "$LOCAL" ]; then
    echo "Error ${repo} is diverged." >&2
    ret=-1
  else
    echo "git ${repo} not found" >&2
    ret=-1
  fi

  echo $ret
}

setTargetBuildVariables(){
  echo "Using ABI toolkit : $ANDROID_ABI" >&2

  if [[ "$ANDROID_ABI" == "armeabi-v7a" ]]; then
    export TARGET=armv7a-linux-androideabi
  elif [[ "$ANDROID_ABI" == "arm64-v8a" ]]; then
    export TARGET=aarch64-linux-android
  elif [[ "$ANDROID_ABI" == "x86" ]]; then
    export TARGET=i686-linux-android
  elif [[ "$ANDROID_ABI" == "x86_64" ]]; then
    export TARGET=x86_64-linux-android
  else
    echo "Unknown ANDROID_ABI $ANDROID_ABI" >&2
    exit 1
  fi
  export API=$ANDROID_NATIVE_API_LEVEL
  export TOOLCHAIN=$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64
  export AR=$TOOLCHAIN/bin/llvm-ar
  export LD=$TOOLCHAIN/bin/ld
  export RANLIB=$TOOLCHAIN/bin/llvm-ranlib
  export STRIP=$TOOLCHAIN/bin/llvm-strip
  export CC="$TOOLCHAIN/bin/clang --target=$TARGET$API"
  export AS=$CC
  export CXX="$TOOLCHAIN/bin/clang++ --target=$TARGET$API"
  export CHOST=$ANDROID_LLVM_TRIPLE
}

function checkLibrary {
  local name static path native
  local "${@}"

  [[ ! -z "${static}" ]] && staticstr=" -static" || staticstr=""
  [[ ! -z "${path}" ]] && pathstr=" -L${path}" || pathstr=""
  [[ ! -z "${excludesys}" ]] && excludesystr=" -nostdinc -nostdinc++" || excludesystr=""
  [[ ! -z "${native}" ]] && cmdstr="gcc" || cmdstr="${CMAKE_C_COMPILER}"

  output="$(${cmdstr} ${excludesystr} ${pathstr} -l${name}${staticstr} 2>&1)";
  if [[ "$output" == *"undefined reference to \`main'"* ]] ||
      [[ "$output" == *"undefined symbol: main"* ]]; then
    echo "${name} found" >&2
  else
    echo "library ${name} not found" >&2
    echo 1
  fi
}

setTargetBuildVariables

if [ $FAILED -eq 1 ]; then
  echo "Script encountered one of more failures"
fi

# Soft check if library archive is already extracted
# TODO hard check for all library needed
if [ ! -f "${GSTREAMER_ROOT}/lib/libglib-2.0.a" ]; then

  downloadAndExtract file="${GSTREAMER_ANDROID_LIBS}.tar.xz" out="${GSTREAMER_ROOT_ANDROID}" path="https://gstreamer.freedesktop.org/data/pkg/android/${GSTREAMER_VERSION}/${GSTREAMER_ANDROID_LIBS}.tar.xz"
else
  echo "Folder already extracted."
fi

#################################################################
##
##    Build cutils
##
#################################################################
export LIBRARY_PATH="${SUBPROJECTS_ROOT}/CUtils/dist/${ANDROID_ABI}/lib:$LIBRARY_PATH"
rebuild=0
if [ ! -z "$(checkLibrary name=cutils static=true path="${SUBPROJECTS_ROOT}/CUtils/dist/${ANDROID_ABI}/lib")" ]; then
  rebuild=1
else
  rebuild=$(checkGitRepoState repo="CUtils" package="cutils")
fi

if [ $rebuild != 0 ]; then
  #Clean up previous build in case of failure. This will prevent from falling back on the old version
  rm -rf "$SUBPROJECTS_ROOT/CUtils/build"
  #OnvifSoapLib depends on it and will also require a rebuild
  rm -rf "$SUBPROJECTS_ROOT/OnvifSoapLib/build"

  pullOrClone path="https://github.com/Quedale/CUtils.git" ignorecache="true"
  buildMakeProject project="cutils" srcdir="CUtils" prefix="$SUBPROJECTS_ROOT/CUtils/dist" cmakedir=".." cmakeargs="-DBUILD_SHARED_LIBS=on" outoftree=true cmakeclean=true
else
  echo "cutils already built"
fi

#################################################################
##
##    Build gsoap
##
#################################################################
gsoap_version=2.8.134
export PATH="$SUBPROJECTS_ROOT/gsoap_$gsoap_version/gsoap-2.8/dist/bin:$PATH"
if [ ! -z "$(checkLibrary name=gsoap native=true static=true path="${SUBPROJECTS_ROOT}/gsoap_$gsoap_version/gsoap-2.8/dist/lib")" ]; then
  downloadAndExtract project="gsoap" file="gsoap_$gsoap_version.zip" path="https://sourceforge.net/projects/gsoap2/files/gsoap_$gsoap_version.zip/download"
  LIBS='-ldl -lpthread' \
  buildMakeProject project="gsoap" srcdir="gsoap_${gsoap_version}/gsoap-2.8" prefix="$SUBPROJECTS_ROOT/gsoap_${gsoap_version}/gsoap-2.8/dist" autogen="skip" configure="--with-openssl=/usr/lib/ssl" localtoolchain="true" distclean=true noabi=true
else
  echo "gsoap already built"
fi

#################################################################
##
##    Build libntlm
##
#################################################################
export LIBRARY_PATH="${SUBPROJECTS_ROOT}/libntlm-1.8/dist/${ANDROID_ABI}/lib:$LIBRARY_PATH"
if [ ! -z "$(checkLibrary name=ntlm static=true path="${SUBPROJECTS_ROOT}/libntlm-1.8/dist/${ANDROID_ABI}/lib")" ]; then
  downloadAndExtract project="libntlm-1.8" file="libntlm-1.8.tar.gz" path="https://mirror.csclub.uwaterloo.ca/nongnu/libntlm/libntlm-1.8.tar.gz" out="${SUBPROJECTS_ROOT}"
  buildMakeProject project="libntlm-1.8" srcdir="libntlm-1.8" prefix="$SUBPROJECTS_ROOT/libntlm-1.8/dist" skipbootstrap="true" configure="--enable-shared=no --host $TARGET" distclean=true
else
  echo "libntlm already built"
fi

################################################################
#
#    Build OnvifSoapLib
#
################################################################
export LIBRARY_PATH="${SUBPROJECTS_ROOT}/CUtils/dist/${ANDROID_ABI}/lib:$LIBRARY_PATH"
export LIBRARY_PATH="${GSTREAMER_ROOT}:$LIBRARY_PATH"
export LIBRARY_PATH="${GSTREAMER_ROOT}/gio:$LIBRARY_PATH"
export LIBRARY_PATH="${GSTREAMER_ROOT}/glib-2.0:$LIBRARY_PATH"
export LIBRARY_PATH="${GSTREAMER_ROOT}/gstreamer-1.0:$LIBRARY_PATH"

export C_INCLUDE_PATH="${GSTREAMER_ROOT}/include:$C_INCLUDE_PATH"
export C_INCLUDE_PATH="${GSTREAMER_ROOT}/include/openssl:$C_INCLUDE_PATH"

rebuild=0
if [ ! -z "$(checkLibrary name=onvifsoap static=true path="${SUBPROJECTS_ROOT}/OnvifSoapLib/dist/${ANDROID_ABI}/lib")" ]; then
  rebuild=1
else
  rebuild=$(checkGitRepoState repo="OnvifSoapLib")
fi

if [ $rebuild != 0 ]; then
  #Clean up previous build in case of failure. This will prevent from falling back on the old version
  rm -rf "$SUBPROJECTS_ROOT/OnvifSoapLib/build/"

  pullOrClone project="onvifsoap" path="https://github.com/Quedale/OnvifSoapLib.git" ignorecache="true"

  GSOAP_SRC_DIR="$SUBPROJECTS_ROOT/gsoap_$gsoap_version/gsoap-2.8" \
  buildMakeProject project="onvifsoap" srcdir="OnvifSoapLib" prefix="$SUBPROJECTS_ROOT/OnvifSoapLib/dist" cmakedir=".." cmakeargs="-DSUBPROJECTS_ROOT=${SUBPROJECTS_ROOT} -DGSTREAMER_ROOT=$GSTREAMER_ROOT -DBUILD_SHARED_LIBS=OFF -DGSOAP_SRC_DIR='$SUBPROJECTS_ROOT/gsoap_$gsoap_version/gsoap-2.8'" bootstrap="--skip-dependencies --skip-gsoap $skipwsdl" outoftree=true cmakeclean=true
else
  echo "onvifsoap already built"
fi


echo "autogen.sh completed successfully"