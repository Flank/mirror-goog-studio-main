package ${packageName};

public class ${activityClass} extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /********************************************************/
        /************* COMPOSE APPS MUST USE KOTLIN *************/
        /******** JAVA IS NOT SUPPORTED, SWITCH TO KOTLIN *******/
        /********************************************************/
        setContentView(${layoutClass}.createInstance(this));
    }

}
