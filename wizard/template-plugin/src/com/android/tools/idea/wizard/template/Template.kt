package com.android.tools.idea.wizard.template

typealias Recipe = RecipeExecutor.(TemplateData) -> Unit

/**
 * Determines in which context (basically a screen) the template should be shown.
 */
enum class WizardUiContext {
  NewProject,
  NewModule,
  MenuEntry,
  ActivityGallery,
  FragmentGallery
}

/**
 * Determines to which menu entry the template belongs.
 */
enum class Category {
  Activity,
  Fragment,
  Application,
  Folder,
  Service,
  UiComponent,
  Automotive,
  XML,
  Wear,
  AIDL,
  Widget,
  Google,
  Other,
}

/**
 * Determines to which form factor the template belongs. Templates with particular form factor may only be rendered in the
 * project of corresponding [Category].
 */
enum class FormFactor {
  Mobile,
  Wear,
  Tv,
  Automotive,
  Things,
  Generic
}

/**
 * Describes a template available in the wizard.
 *
 * This interface is used by the wizard in 3 steps:
 * 1. User is presented an option to select a template, for example when creating a new Module, browsing activity gallery,
 * or choosing from the New -> X menu. Selection of the Template depends on fields like [Category], [FormFactor], etc.
 * 2. After the user selects a template, the wizards will call [Parameter]s to build the UI.
 * 3. Recipe is executed with parameters' values supplied by the user in the UI.
 **/
interface Template {
  /**
   * When a [Template] is chosen by the user, the [widgets] are used by the Wizards to build the user UI.
   *
   * Usually, it displays an input for [Parameter].
   */
  val widgets: Collection<Widget<*>>
  /**
   * Usually, a user provides [Parameter.value]s by interaction with the UI [widgets].
   **/
  val parameters: Collection<Parameter<*>> get() = widgets.filterIsInstance<ParameterWidget<*>>().map { it.parameter }
  /**
   * A template name which is also used as identified.
   *
   * @see revision
   */
  val name: String
  /**
   * If there are multiple templates with the same name, a template with the highest [revision] will be used.
   * It provides an ability to override default templates with the custom ones.
   */
  val revision: Int
  val description: String
  /**
   * Minimum sdk version required to build this template.
   * If minSdkVersion in build.gradle is less than [minSdk], the template will not be available (e.g. action will be disabled).
   */
  val minSdk: Int
  /**
   * Minimum compile sdk version required to build this template.
   * If compileSdkVersion in build.gradle is less than [minCompileSdk], the template will not be available (e.g. action will be disabled).
   */
  val minCompileSdk: Int
  /**
   * Indicates that the template could be rendered only for androidX project.
   */
  val requireAndroidX: Boolean
  val category: Category
  val formFactor: FormFactor
  /** Recipe used to generate this [Template] output. It will be called after the user provides values for all [Parameter]s. */
  val recipe: Recipe
  /** The template will be shown only in given context. Should include all possible contexts by default. */
  val uiContexts: Collection<WizardUiContext>

  /** Returns a thumbnail which are drawn in the UI. It will be called every time when any parameter is updated. */
  // TODO(qumeric): consider using IconLoader and/or wizard icons.
  // TODO(qumeric): rename to pick thumb?
  fun thumb(): Thumb

  /**
   * Represent absence of a [Template] (null object pattern).
   */
  companion object NoActivity: Template {
    override val widgets: Collection<Widget<*>> = listOf()
    override val uiContexts: Collection<WizardUiContext> get() = TODO()
    override val recipe: Recipe get() = throw UnsupportedOperationException()
    override val revision: Int = 1
    override val name: String = "No Activity"
    override val description: String = "Creates a new empty project"
    override val minSdk: Int = 1
    override val minCompileSdk: Int = 1
    override val requireAndroidX: Boolean = false
    override val category: Category = Category.Activity
    override val formFactor: FormFactor = FormFactor.Mobile
    override fun thumb() = Thumb.NoThumb
  }
}
