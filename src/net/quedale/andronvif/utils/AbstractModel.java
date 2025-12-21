package net.quedale.andronvif.utils;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import org.jspecify.annotations.NonNull;

public abstract class AbstractModel<T extends Enum<T>> extends ViewModel {
    private final MutableLiveData<T> state = new MutableLiveData<>();

    public void setState(T state){
        this.state.postValue(state);
    }

    public void observeState(@org.jspecify.annotations.NonNull LifecycleOwner owner, @NonNull Observer<? super T> observer){
        state.observe(owner, observer);
    }
}
