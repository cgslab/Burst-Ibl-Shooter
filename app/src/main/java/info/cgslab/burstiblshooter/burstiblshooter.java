package info.cgslab.burstiblshooter;

import android.app.Application;

import timber.log.Timber;

public class burstiblshooter extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());

        }
    }

}
