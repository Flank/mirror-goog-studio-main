package ${packageName};

import ${superClassFqcn};
import android.os.Bundle;
import ${packageName}.${fragmentPackage}.${fragmentClass};

public class ${activityClass} extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.${activityLayout});
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, ${fragmentClass}.newInstance())
                .commitNow();
        }
    }
}
