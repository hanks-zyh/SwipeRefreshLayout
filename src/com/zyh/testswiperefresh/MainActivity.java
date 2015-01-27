package com.zyh.testswiperefresh;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.View.OnClickListener;

public class MainActivity extends FragmentActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		findViewById(R.id.button1).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				getFragmentManager().beginTransaction().replace(R.id.frameLayout, new MyListFragment()).commit();
			}
		});
		findViewById(R.id.button2).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				getFragmentManager().beginTransaction().replace(R.id.frameLayout, new MyScrollViewFragment())
						.commit();
			}
		});
	}

}
