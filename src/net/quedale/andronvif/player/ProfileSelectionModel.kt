package net.quedale.andronvif.player

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.quedale.andronvif.utils.AbstractModel

class ProfileSelectionModel(application: Application) : AbstractModel<ProfileSelectionModel.State>(
    application
) {
    enum class State {
        INITIALIZING,
        LOADING,
        DONE
    }


    fun start(){
        setState(State.INITIALIZING)
        kotlinx.coroutines.MainScope().launch(Dispatchers.IO) { //Launching with ApplicationScope since this list is the primary model
            coroutineScope {
                setState(State.LOADING)

                Thread.sleep(1000);
            }
            setState(State.DONE)
        }
    }
}