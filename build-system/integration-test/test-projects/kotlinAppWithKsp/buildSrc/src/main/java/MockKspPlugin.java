import org.gradle.api.Plugin;
import org.gradle.api.Project;

// A mock KSP plugin to avoid the hard dependency on a specific KGP version that a real KSP plugin
// would need.
public class MockKspPlugin implements Plugin<Project> {
    public void apply(Project project) {
        project.getConfigurations().maybeCreate("ksp");
    }
}
