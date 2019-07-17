package ${packageName}.ui.login;

import ${getMaterialComponentName('android.arch.lifecycle.ViewModel', useAndroidX)};
import ${getMaterialComponentName('android.arch.lifecycle.ViewModelProvider', useAndroidX)};
import ${getMaterialComponentName('android.support.annotation.NonNull', useAndroidX)};

import ${packageName}.data.LoginDataSource;
import ${packageName}.data.LoginRepository;

/**
 * ViewModel provider factory to instantiate LoginViewModel.
 * Required given LoginViewModel has a non-empty constructor
 */
public class LoginViewModelFactory implements ViewModelProvider.Factory {

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(LoginViewModel.class)) {
            return (T) new LoginViewModel(LoginRepository.getInstance(new LoginDataSource()));
        } else {
            throw new IllegalArgumentException("Unknown ViewModel class");
        }
    }
}
