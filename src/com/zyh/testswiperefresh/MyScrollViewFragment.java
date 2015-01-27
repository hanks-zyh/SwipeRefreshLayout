package com.zyh.testswiperefresh;

import java.util.Random;

import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;

public class MyScrollViewFragment extends Fragment {

	private SwipeRefreshLayout		swipeRefreshLayout;
	private ArrayAdapter<String>	adapter;
	private LinearLayout					linearlayout;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment_scroll, container, false);
		init(v);
		return v;
	}

	private void init(View v) {
		linearlayout = (LinearLayout) v.findViewById(R.id.linearlayout);
		swipeRefreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.swipeRefreshLayout);
		swipeRefreshLayout.setColorSchemeResources(android.R.color.holo_blue_bright,
				android.R.color.holo_green_light, android.R.color.holo_orange_light, android.R.color.holo_red_light);
		swipeRefreshLayout.setOnRefreshListener(new OnRefreshListener() {
			@Override
			public void onRefresh() {
				new Handler().postDelayed(new Runnable() {
					public void run() {
						Button child = new Button(getActivity());
						child.setText("ÐÂÌí¼Óitem£º"+ new Random().nextInt());
						linearlayout.addView(child,0);
						swipeRefreshLayout.setRefreshing(false);
					}
				}, 3000);
			}
		});

	}
}
