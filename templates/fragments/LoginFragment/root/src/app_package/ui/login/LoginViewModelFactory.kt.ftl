package ${escapeKotlinIdentifiers(packageName)}.ui.login

import ${getMaterialComponentName('android.arch.lifecycle.ViewModel', useAndroidX)}
import ${getMaterialComponentName('android.arch.lifecycle.ViewModelProvider', useAndroidX)}
import ${escapeKotlinIdentifiers(packageName)}.data.LoginDataSource
import ${escapeKotlinIdentifiers(packageName)}.data.LoginRepository

/**
 * ViewModel provider factory to instantiate LoginViewModel.
 * Required given LoginViewModel has a non-empty constructor
 */
class LoginViewModelFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            return LoginViewModel(
                loginRepository = LoginRepository(
                    dataSource = LoginDataSource()
                )
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
