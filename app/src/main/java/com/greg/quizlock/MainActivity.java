package com.greg.quizlock;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Random;
import javax.net.ssl.HttpsURLConnection;
import org.json.*;

// Client ID code: UY36DFHPRt
// Secret Key: EnsfAFS2zuravvu5wnBtbp
// Redirect URI: https://www.grachyahovhannisyan.com/quizlock


public class MainActivity extends AppCompatActivity {

    String answer;
    int wrongCounter = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.v("View", "Setting content view");

        // if first time launching app or don't have the database, then go to the website,
        // get the data and write it to the database
        if(!doesDatabaseExist(MainActivity.this, "TERMS")){
            new NetworkingAsync().execute(new Void[0]);
            Log.v("Database", "Database does not exist");
        }else{
            Log.v("Database", "Database does exist");
            QuizDatabaseHelper helper = new QuizDatabaseHelper(MainActivity.this);
            Log.v("rando","Calling random function");
            answer = helper.getRandomTerm();
        }

        // Set the key listener for the EditText
        final EditText editText = (EditText) findViewById(R.id.answer);
        editText.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    String input = editText.getText().toString();
                    SQLiteDatabase database = null;
                    if(answer.toLowerCase().equals(input)){
                        // Write to the database that the answer was correct.
                        // Launch the default lock screen
                        try {
                            QuizDatabaseHelper helper = new QuizDatabaseHelper(MainActivity.this);
                            database = helper.getWritableDatabase();
                            Cursor cursor = database.rawQuery("SELECT NUMBER_OF_CORRECT FROM TERMS " +
                                    "WHERE TERM = '" + answer + "'", null);
                            cursor.moveToFirst();
                            ContentValues contentValues = new ContentValues();
                            contentValues.put("NUMBER_OF_CORRECT", cursor.getInt(0) + 1);
                            database.update("TERMS", contentValues,"TERM = ?", new String[]{answer});
                            database.close();
                            Log.v("Correct", "Successfully wrote correct answer to database");
                            finish();
                        }catch (SQLiteException e){
                            Log.v("Writing", "Failed to write to database after correct answer");
                            e.printStackTrace();
                        }finally {
                            if(database.isOpen()) database.close();
                        }

                        Toast.makeText(MainActivity.this, "Correct !", Toast.LENGTH_SHORT).show();
                        // Launch the default lock screen

                    }else if (wrongCounter >= 1){
                        // Decrement the wrongCounter
                        // Inform the user that it was wrong.
                        // Write to the database that it was wrong.
                        // Let the user try again
                        Log.v("Wrong Answer", "Wrong answer. Correct is: " + answer + " and input is: " + input);
                        try {
                            QuizDatabaseHelper helper = new QuizDatabaseHelper(MainActivity.this);
                            database = helper.getWritableDatabase();
                            Cursor cursor = database.rawQuery("SELECT NUMBER_OF_INCORRECT FROM TERMS " +
                                    "WHERE TERM = '" + answer +"'", null);
                            cursor.moveToFirst();
                            ContentValues contentValues = new ContentValues();
                            contentValues.put("NUMBER_OF_INCORRECT", cursor.getInt(0) + 1);
                            database.update("TERMS", contentValues,"TERM = ?", new String[]{answer});
                            database.close();
                            Log.v("Incorrect", "Successfully wrote incorrect answer to database");
                        }catch (SQLiteException e){
                            Log.v("Writing", "Failed to write to database after incorrect answer");
                            e.printStackTrace();
                        }finally {
                            if(database.isOpen()) database.close();
                        }

                        wrongCounter--;
                        if(wrongCounter == 0){
                            // launch the lock screen
                            // Tell the correct answer
                            Toast.makeText(MainActivity.this, "The answer was: " + answer, Toast.LENGTH_LONG).show();
                            finish();
                        }
                        editText.setHint("Incorrect, try again");
                        editText.setText("");
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private static boolean doesDatabaseExist(Context context, String dbName) {
            File dbFile = context.getDatabasePath(dbName);
            return dbFile.exists();
    }


    // Used to get the initial list of study cards from quizlet
// Accesses public sets by direct url get request

    public class NetworkingAsync extends AsyncTask<Void, Void, JSONArray> {

        private static final String getSetsUrl =
                "https://api.quizlet.com/2.0/sets/144842783?access_token=v4j6KFkZXTd3N28k9YE3Wuh7zasaKgwpTnfZ5rvW&whitespace=1";
        HttpsURLConnection httpsURLConnection;
        String returnJson;
        JSONArray jsonArray = null;

        public JSONArray doInBackground(Void... voids){
            try {
                URL quizletUrl = new URL(getSetsUrl);

                httpsURLConnection= (HttpsURLConnection) quizletUrl.openConnection();
                Log.v("shit", "connected");
                InputStream in = new BufferedInputStream(httpsURLConnection.getInputStream());
                InputStreamReader reader = new InputStreamReader(in);
                BufferedReader bufferedReader = new BufferedReader(reader);
                StringBuilder builder = new StringBuilder();
                String tempStr;
                while ((tempStr = bufferedReader.readLine()) !=null){
                    builder.append(tempStr);
                }

                JSONObject jsonObject = new JSONObject(builder.toString());
                jsonArray = jsonObject.getJSONArray("terms");

            }catch (MalformedURLException e){
                Log.v("URL", "Bad url error");
                return jsonArray;
            }catch (IOException e) {
                Log.v("IO", "Bad io error");
                Log.v("IO", e.toString());
                return jsonArray;
            }catch (JSONException e){
                Log.v("Json", "Bad json error");
                Log.v("JSon", e.toString());
                return jsonArray;
            }finally {
                httpsURLConnection.disconnect();
            }
            return jsonArray;
        }

        @Override
        public void onPostExecute(JSONArray success){
            if (success==null){
                Log.v("Error", "Could not connect to url");
            }else{
                Log.v("Success", "Connected to url successfully");
                new DatabaseAsync().execute(success);
            }
        }
    }

    // Used to access the database and get an entry

    public class DatabaseAsync extends AsyncTask<JSONArray, Void, Boolean> {

        SQLiteDatabase database = null;
        QuizDatabaseHelper helper;

        @Override
        public void onPreExecute(){
            // Called by the main thread
        }

        @Override
        public Boolean doInBackground(JSONArray... jsonArrays){
            try {
                helper = new QuizDatabaseHelper(MainActivity.this);
                database = helper.getWritableDatabase();
                ContentValues contentValues = new ContentValues();
                Log.v("Length", "There are " + jsonArrays[0].length() + " items to add");
                for(int i = 0; i<jsonArrays[0].length(); i++) {
                    //Log.v("JsonStuff", jsonArrays[0].getJSONObject(i+1).getString("term"));
                    //Log.v("JsonStuff", jsonArrays[0].getJSONObject(i+1).getString("definition"));

                    contentValues.put("TERM", jsonArrays[0].getJSONObject(i).getString("term"));
                    contentValues.put("DEFINITION", jsonArrays[0].getJSONObject(i).getString("definition"));
                    contentValues.put("NUMBER_OF_INCORRECT", 0);
                    contentValues.put("NUMBER_OF_CORRECT", 0);
                    database.insert("TERMS", null, contentValues);
                }
                Log.v("Write", "Wrote to database successfully");
            }catch (SQLiteException e){
                Log.v("WritingDBError", "Unable to write to database");
                return false;
            }catch (JSONException e){
                Log.v("JSON", "Some error with the json");
                return false;
            }finally {
                database.close();
            }
            return true;
        }

        @Override
        public void onPostExecute(Boolean success){
            Log.v("Random", "Calling random function");
            answer = helper.getRandomTerm();
        }
    }

    // Used for reading and writing to the database
    // Takes

    public class QuizDatabaseHelper extends SQLiteOpenHelper {

        private static final String DB_NAME = "TERMS";
        private static final int DB_VERSION = 1;

        public String getRandomTerm(){
            // Gets a random number in range of the number of items in the database
            // Gets the Term and definition
            Random random = new Random();
            String answer = null;
            String question = null;

            try {
                QuizDatabaseHelper helper = new QuizDatabaseHelper(MainActivity.this);
                SQLiteDatabase database = helper.getWritableDatabase();
                Cursor cursor = database.rawQuery("SELECT MAX(_id) AS _id FROM TERMS", null);
                cursor.moveToFirst();
                Log.v("Cursor",Integer.toString(cursor.getCount()));

                int randomId = random.nextInt(cursor.getInt(cursor.getColumnIndex("_id")) + 1);
                if(randomId == 0){
                    randomId++;
                }
                cursor = database.rawQuery("SELECT TERM, DEFINITION FROM TERMS WHERE _id = " +
                randomId, null);
                cursor.moveToFirst();
                question = cursor.getString(1);
                answer = cursor.getString(0);
                TextView textView = (TextView) findViewById(R.id.question);
                textView.setText(question);

            }catch (SQLiteException e){
                Log.v("SQL", e.toString());
            }
            return answer;
        }

        QuizDatabaseHelper(Context context){
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase database){
            String createTable = "CREATE TABLE TERMS (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "TERM TEXT, DEFINITION TEXT, NUMBER_OF_INCORRECT INTEGER, NUMBER_OF_CORRECT INTEGER)";

            database.execSQL(createTable);
            Log.v("Create", "Created database successfully");
        }

        public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion){
        }
    }


    @Override
    public void onDestroy(){
        super.onDestroy();
        //android.os.Process.killProcess(android.os.Process.myPid());
    }
}