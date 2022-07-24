package com.dayani.m.roboplatform;

/*
  1. This activity launches target activities with
    an intent containing the fully-qualified name of that activity.
  2. Important Note: without saving these fragments on backstack,
    when you back from the last fragment, it'll still be visible!
*/


import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.dayani.m.roboplatform.utils.ActivityRequirements.Requirement;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.OnRequestPageChange;

import java.util.ArrayList;


public class MainActivity extends AppCompatActivity
        implements FrontPanelFragment.OnFrontPanelInteractionListener,
        RequirementsFragment.OnRequirementsInteractionListener,
        OnRequestPageChange {

    private static final String TAG = MainActivity.class.getSimpleName();

    private RequirementsFragment requirementsFragment;

    private Class<?> targetActivity;

    private FragmentManager fragmentManager;
    private FragmentTransaction fragmentTransaction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fragmentManager = getSupportFragmentManager();
        fragmentTransaction = fragmentManager.beginTransaction();

        FrontPanelFragment fpFragment = FrontPanelFragment.newInstance();
        fragmentTransaction.add(R.id.fragment_container, fpFragment).commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // Handle item selection
        int itemId = item.getItemId();
        if (itemId == R.id.settings) {
            changeSettings();
            return true;
        }
        else if (itemId == R.id.mainRefresh) {
            Log.d(TAG, "option item refresh is selected.");
            return true;
        }
        else if (itemId == R.id.help) {
            toastMsgShort("Duhh...! This is a Robotic Platform app!");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult");

        Fragment currentFrame = fragmentManager.findFragmentById(R.id.fragment_container);
        //if (currentFrame isntanceOf('FrontPanelFragment')) ...
        if (currentFrame != null && currentFrame.isVisible())
            currentFrame.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionsResult");

        Fragment currentFrame = fragmentManager.findFragmentById(R.id.fragment_container);
        //if (currentFrame isntanceOf('FrontPanelFragment')) ...
        if (currentFrame != null && currentFrame.isVisible())
            currentFrame.onRequestPermissionsResult(requestCode,permissions,grantResults);
    }

    /*--------------------------------------------------------------------------------------------*/

    @Override
    public void onFrontPanelInteraction(Class<?> targetActivity) {

        Log.d(TAG, "onFrontPanelInteraction");
        this.targetActivity = targetActivity;

        fragmentTransaction = fragmentManager.beginTransaction();
        requirementsFragment = RequirementsFragment.newInstance();
        fragmentTransaction.replace(R.id.fragment_container, requirementsFragment).
                addToBackStack("root").commit();
    }

    @Override
    public void onRequestPageChange(Fragment targetFragment, String backStackName) {

        Log.d(TAG, "onRequestPageChange");
        fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, targetFragment).
                addToBackStack(backStackName).commit();
    }

    @Override
    public void onRequirementInteraction(Requirement requirement, boolean isPassed,
                                         String connType, String backStackName) {

        Log.d(TAG, "onRequirementInteraction: "+requirement+':'+isPassed);
        requirementsFragment.requirementHandled(requirement, connType);
        fragmentManager.popBackStack(backStackName, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    @Override
    public void startTargetActivity(String connType) {

        Log.d(TAG, "startTargetActivity: "+targetActivity.getSimpleName());
        Intent intent = new Intent(this, targetActivity);
        intent.putExtra(AppGlobals.KEY_CONNECTION_TYPE,connType);
        startActivity(intent);
    }

    /*--------------------------------------------------------------------------------------------*/

    public void changeSettings() {

        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra(AppGlobals.KEY_TARGET_ACTIVITY, SettingsActivity.class.getSimpleName());
        //finish();
        startActivity(intent);
    }

    /*--------------------------------------------------------------------------------------------*/

    public void toastMsgShort(String msg) {
        Toast.makeText(this.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    /*--------------------------------------------------------------------------------------------*/
}

