package com.bandwidth.tv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

public class SettingsActivity extends PreferenceActivity {
    
    private static final String TAG = SettingsActivity.class.getSimpleName();

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        this.setResult(RESULT_CANCELED);
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String sheetId = prefs.getString("sheet_id", null);
        Log.d(TAG, "sheet_id -> " + sheetId);
        
        ListPreference sheetPref = new ListPreference(this);
        sheetPref.setKey("sheet_id");
        sheetPref.setNegativeButtonText(null);
        sheetPref.setPositiveButtonText(null);
        sheetPref.setTitle("Configured sheet name");
        sheetPref.setEnabled(false);
        
        try {
            JSONObject data = new JSONObject(prefs.getString("sheets", null));
            setSheets(sheetPref, data);
            if (sheetId != null) {
                sheetPref.setValue(sheetId);
            }
        } catch (Throwable e) {
            Log.d(TAG, "could not get config", e);
        }
        
        addPreferencesFromResource(R.xml.pref);
        getPreferenceScreen().addPreference(sheetPref);

        bindPreferenceSummaryToValue(findPreference("sheet_id"));
        
        Preference refreshPref = new Preference(this);
        refreshPref.setTitle("Refresh");
        refreshPref.setSummary("Click to refresh configuration data from spreadsheet");
        refreshPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            
            @Override
            public boolean onPreferenceClick(Preference preference) {
                setResult(RESULT_OK);
                finish();
                return true;
            }
        });
        getPreferenceScreen().addPreference(refreshPref);
        
        new ConfigFetch().execute();
    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference
                        .setSummary(index >= 0 ? listPreference.getEntries()[index]
                                : null);

            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     * 
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference
                .setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(
                preference,
                PreferenceManager.getDefaultSharedPreferences(
                        preference.getContext()).getString(preference.getKey(),
                        ""));
    }
    
    private class Sheet {
        public int id;
        public String name;
    }
    
    private class ConfigFetch extends AsyncTask<Void, Void, JSONObject> {

        @Override
        protected JSONObject doInBackground(Void... params) {
//            List<Sheet> sheets = new ArrayList<Sheet>();
            JSONObject data = null;
            
            HttpClient client = new DefaultHttpClient();
            HttpContext ctx = new BasicHttpContext();
            HttpGet get = new HttpGet("https://script.google.com/macros/s/AKfycbyZz8snOZuQMsj_aoJtKSNJyAh2sFsNjLvCCtC-aRCy90jUlKue/exec");
            
            try {
                HttpResponse res = client.execute(get, ctx);
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(res.getEntity().getContent()));
                StringBuilder builder = new StringBuilder();
                
                for (String line = null; (line = reader.readLine()) != null;) {
                    builder.append(line);
                }
                
                reader.close();
                
                data = new JSONObject(builder.toString());
                
//                JSONArray sheetsJSON = data.getJSONArray("sheets");
//                
//                int numSheets = sheetsJSON.length();
//                for (int i = 0; i < numSheets; i++) {
//                    JSONObject sheetJSON = sheetsJSON.getJSONObject(i);
//                    Sheet sheet = new Sheet();
//                    sheet.id = sheetJSON.getInt("id");
//                    sheet.name = sheetJSON.getString("name");
//                    sheets.add(sheet);
//                }
                
            } catch (ClientProtocolException e) {
                Log.d(TAG, "could not get config", e);
            } catch (IOException e) {
                Log.d(TAG, "could not get config", e);
            } catch (JSONException e) {
                Log.d(TAG, "could not get config", e);
            }
            
            return data;
        }
        
        @Override
        protected void onPostExecute(JSONObject results) {
            receiveSheets(results);
        }
        
    }
    
    protected void receiveSheets(JSONObject data) {
        Log.d(TAG, "sheets -> " + data);
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        Editor editor = prefs.edit();
        editor.putString("sheets", data.toString());
        editor.commit();
        
        setSheets((ListPreference)findPreference("sheet_id"), data);
    }
    
    protected void setSheets(ListPreference pref, JSONObject data) {
        try {
            JSONArray sheetsJSON = data.getJSONArray("sheets");
            
            CharSequence[] entries = new CharSequence[sheetsJSON.length()];
            CharSequence[] entryValues = new CharSequence[sheetsJSON.length()];
            
            int numSheets = sheetsJSON.length();
            for (int i = 0; i < numSheets; i++) {
                JSONObject sheetJSON = sheetsJSON.getJSONObject(i);
                entries[i] = sheetJSON.getString("name");
                entryValues[i] = Integer.toString(sheetJSON.getInt("id"));
            }
            
            pref.setEntries(entries);
            pref.setEntryValues(entryValues);
            
            pref.setEnabled(true);
        } catch (JSONException e) {
            Log.d(TAG, "could not get config", e);
        }
        
    }
}
