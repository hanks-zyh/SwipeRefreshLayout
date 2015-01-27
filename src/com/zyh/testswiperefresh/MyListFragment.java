package com.zyh.testswiperefresh;

import java.util.ArrayList;
import java.util.List;
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
import android.widget.ListView;

public class MyListFragment extends Fragment {

	private SwipeRefreshLayout		swipeRefreshLayout;
	private ListView							listView;
	private ArrayAdapter<String>	adapter;
	private List<String>					data;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment_pictext, container, false);
		init(v);
		return v;
	}

	private void init(View v) {
		listView = (ListView) v.findViewById(R.id.listView);
		data = new ArrayList<String>();
		for (int i = 0; i < 50; i++) {
			data.add("我是测试item：" + i);
		}
		adapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, data);
		listView.setAdapter(adapter);
		
		//findview
		swipeRefreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.swipeRefreshLayout);
		//设置卷内的颜色
		swipeRefreshLayout.setColorSchemeResources(android.R.color.holo_blue_bright,
				android.R.color.holo_green_light, android.R.color.holo_orange_light, android.R.color.holo_red_light);
		//设置下拉刷新监听
		swipeRefreshLayout.setOnRefreshListener(new OnRefreshListener() {
			@Override
			public void onRefresh() {
				new Handler().postDelayed(new Runnable() {
					public void run() {
						data.add(0, "添加新的item" + new Random().nextInt());
						adapter.notifyDataSetChanged();
						//停止刷新动画
						swipeRefreshLayout.setRefreshing(false);
					}
				}, 3000);
			}
		});

	}
}
