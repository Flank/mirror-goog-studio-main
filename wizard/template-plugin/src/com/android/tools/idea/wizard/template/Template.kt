package com.android.tools.idea.wizard.template

typealias Recipe = RecipeExecutor.(TemplateData) -> Unit

/**
 * Determines in which context (basically a screen) the template should be shown.
 * Note: [NewProjectExtraDetail] should only be used if [NewProject] is simultaneously used.
 */
enum class WizardUiContext {
  NewProject,
  /** Show extra step for Activity details in New Project. */
  NewProjectExtraDetail,
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
  Compose,
  Other,
}

/**
 * Representations of all Android hardware devices we can target when building an app.
 */
enum class FormFactor(val displayName: String) {
  Mobile("Phone and Tablet"),
  Wear("Wear OS"),
  Tv("Android TV"),
  Automotive("Automotive"),
  Generic("Generic");

  override fun toString(): String {
    return displayName
  }
}

enum class TemplateConstraint {
  AndroidX,
  Kotlin
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
   * A template name which is also used as identified.
   */
  val name: String
  /**
   * A textual description which is shown in wizards UIs.
   */
  val description: String
  /**
   * Address of an external website with more details about the template.
   */
  val documentationUrl: String?
  /** Returns a thumbnail which are drawn in the UI. It will be called every time when any parameter is updated. */
  // TODO(qumeric): consider using IconLoader and/or wizard icons.
  fun thumb(): Thumb

  /**
   * When a [Template] is chosen by the user, the [widgets] are used by the Wizards to build the user UI.
   *
   * Usually, it displays an input for [Parameter].
   */
  val widgets: Collection<Widget<*>>
  /**
   * Usually, a user provides [Parameter.value]s by interaction with the UI [widgets].
   */
  val parameters: Collection<Parameter<*>> get() = widgets.filterIsInstance<ParameterWidget<*>>().map { it.parameter }

  /**
   * Recipe used to generate this [Template] output. It will be called after the user provides values for all [Parameter]s.
   */
  val recipe: Recipe

  /** The template will be shown only in given context. Should include all possible contexts by default. */
  val uiContexts: Collection<WizardUiContext>
  /**
   * Minimum sdk version required to build this template.
   * If minSdkVersion in build.gradle is less than [minSdk], the template will not be available (e.g. action will be disabled).
   */
  val minSdk: Int
  /**
   * Determines to which menu entry the template belongs.
   */
  val category: Category
  /**
   * Determines to which form factor the template belongs. Templates with particular form factor may only be rendered in the
   * project of corresponding [Category].
   */
  val formFactor: FormFactor
  /** Conditions under which the template may be rendered. For example, some templates only support AndroidX */
  val constraints: Collection<TemplateConstraint>

  /**
   * Represent absence of a [Template] (null object pattern).
   */
  companion object NoActivity: Template {
    override val widgets: Collection<Widget<*>> = listOf()
    override val uiContexts: Collection<WizardUiContext> get() = listOf(WizardUiContext.ActivityGallery)
    override val constraints: Collection<TemplateConstraint> = listOf()
    override val recipe: Recipe get() = throw UnsupportedOperationException()
    override val name: String = "No Activity"
    override val description: String = "Creates a new empty project"
    override val documentationUrl: String? = null
    override val minSdk: Int = 1
    override val category: Category = Category.Activity
    override val formFactor: FormFactor = FormFactor.Mobile
    override fun thumb() = Thumb.NoThumb
  }
}
