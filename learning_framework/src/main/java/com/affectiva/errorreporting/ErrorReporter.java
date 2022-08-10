/**
 * Copyright (c) 2016 Affectiva Inc.
 * See the file license.txt for copying permission.
 */

package com.affectiva.errorreporting;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;

import com.affectiva.affdexme.R;

import java.text.DateFormat;
import java.util.Date;

public class ErrorReporter extends Activity implements View.OnClickListener {

    String errorMessage;
    Button sendButton;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE); // make a dialog without a titlebar
        setContentView(R.layout.error_reporter);

        Throwable error = (Throwable) getIntent().getSerializableExtra("affdexme_error");
        if (error != null) {

            StringBuilder builder = new StringBuilder();
            builder.append("AffdexMe Error Report:");
            builder.append(DateFormat.getDateTimeInstance().format(new Date()));
            builder.append("\n");
            builder.append(error.getMessage());
            builder.append(("\n"));

            StackTraceElement[] stackTraceElements = error.getStackTrace();
            for (StackTraceElement element : stackTraceElements) {
                builder.append("\n");
                builder.append(element.toString());
            }
            errorMessage = builder.toString();
        } else {
            errorMessage = "Failed to catch error.";
        }

        sendButton = (Button) findViewById(R.id.send_button);
        sendButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("plain/text");
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"sdk@affectiva.com"});
        intent.putExtra(Intent.EXTRA_SUBJECT, "AffdexMe Crash Report");
        intent.putExtra(Intent.EXTRA_TEXT, errorMessage); // do this so some email clients don't complain about empty body.
        startActivity(intent);
        finish();
    }
}
