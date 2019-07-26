package ${packageName};

import ${superClassFqcn};
import android.os.Bundle;

public class ${activityClass} extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
<#if generateLayout>
        setContentView(R.layout.${layoutName});
</#if>
    }
}
