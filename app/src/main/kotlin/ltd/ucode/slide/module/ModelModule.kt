package ltd.ucode.slide.module

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import ltd.ucode.slide.ui.login.LoginModel
import ltd.ucode.slide.ui.main.MainModel
import ltd.ucode.slide.ui.submissionView.SubmissionsViewModel

@Module
@InstallIn(ViewModelComponent::class)
object ModelModule {
    @Provides
    @ViewModelScoped
    fun providesLoginModel(): LoginModel =
        LoginModel()

    @Provides
    @ViewModelScoped
    fun providesMainModel(): MainModel =
        MainModel()

    @Provides
    @ViewModelScoped
    fun providesSubmissionsViewModel(): SubmissionsViewModel =
        SubmissionsViewModel()
}
