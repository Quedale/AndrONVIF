package net.quedale.andronvif.utils;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class AbstractController<T extends ViewModel, U extends Fragment> {
    private T model;
    private final U fragment;
    public AbstractController(U fragment){
        this.fragment = fragment;
    }

    public U getFragment(){
        return fragment;
    }


    public T getModel(){
        if(model == null){
            Type genericSuperclass = getClass().getGenericSuperclass();
            if(genericSuperclass == null) return null;
            Type[] actualTypeArguments = ((ParameterizedType) genericSuperclass).getActualTypeArguments();
            //This is a constant configuration defined as part of this class
            @SuppressWarnings("unchecked") Class<T> vm_clazz = (Class<T>) actualTypeArguments[0];
            model = new ViewModelProvider(fragment).get(vm_clazz);
        }
        return model;
    }
}
