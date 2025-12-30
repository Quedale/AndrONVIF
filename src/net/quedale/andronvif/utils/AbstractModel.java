package net.quedale.andronvif.utils;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import org.jspecify.annotations.NonNull;

public abstract class AbstractModel<T extends Enum<T>> extends AndroidViewModel {
    private final MutableLiveData<T> state = new MutableLiveData<>();

    public AbstractModel(@androidx.annotation.NonNull Application application) {
        super(application);
    }

    public void setState(T state){
        this.state.postValue(state);
    }

    public void observeState(@org.jspecify.annotations.NonNull LifecycleOwner owner, @NonNull Observer<? super T> observer){
        state.observe(owner, observer);
    }
}
