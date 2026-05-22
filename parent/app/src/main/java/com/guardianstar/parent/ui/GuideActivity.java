package com.guardianstar.parent.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.guardianstar.parent.R;

import java.util.ArrayList;
import java.util.List;

public class GuideActivity extends AppCompatActivity {

    private ViewPager viewPager;
    private GuidePagerAdapter adapter;
    private List<GuidePage> pages;
    private Button btnNext, btnSkip;
    private View[] dots;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        initPages();
        initViews();
        setupViewPager();
    }

    private void initPages() {
        pages = new ArrayList<>();
        pages.add(new GuidePage(
                "欢迎使用守护星",
                "为您的孩子提供全方位的数字守护，让科技成为成长的好伙伴",
                R.drawable.ic_shield_large
        ));
        pages.add(new GuidePage(
                "应用时长限制",
                "为每个应用设置每日使用时长，到达限制后自动锁定，帮助孩子养成良好的使用习惯",
                R.drawable.ic_timer
        ));
        pages.add(new GuidePage(
                "网页内容过滤",
                "智能拦截不良网站，支持关键词过滤，为孩子打造健康的上网环境",
                R.drawable.ic_filter
        ));
        pages.add(new GuidePage(
                "智能定时任务",
                "设置睡觉时间自动锁定，学习模式只允许白名单应用，时间管理更智能",
                R.drawable.ic_schedule
        ));
        pages.add(new GuidePage(
                "SOS紧急求助",
                "孩子遇到危险时，一键发送位置信息到您的手机，安全守护无处不在",
                R.drawable.ic_sos
        ));
    }

    private void initViews() {
        viewPager = findViewById(R.id.viewPager);
        btnNext = findViewById(R.id.btnNext);
        btnSkip = findViewById(R.id.btnSkip);

        btnNext.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            if (current < pages.size() - 1) {
                viewPager.setCurrentItem(current + 1);
            } else {
                startRoleSelection();
            }
        });

        btnSkip.setOnClickListener(v -> startRoleSelection());
    }

    private void setupViewPager() {
        adapter = new GuidePagerAdapter(pages);
        viewPager.setAdapter(adapter);

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                updateDots(position);
                if (position == pages.size() - 1) {
                    btnNext.setText("开始使用");
                } else {
                    btnNext.setText("下一步");
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });

        initDots();
    }

    private void initDots() {
        ViewGroup dotsLayout = findViewById(R.id.dotsLayout);
        dots = new View[pages.size()];

        for (int i = 0; i < pages.size(); i++) {
            dots[i] = new View(this);
            int size = (int) (8 * getResources().getDisplayMetrics().density);
            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(size, size);
            params.width = size;
            params.height = size;
            ViewGroup.MarginLayoutParams marginParams = new ViewGroup.MarginLayoutParams(params);
            marginParams.setMargins(size / 2, 0, size / 2, 0);
            dots[i].setLayoutParams(marginParams);
            dots[i].setBackgroundResource(R.drawable.dot_inactive);
            dotsLayout.addView(dots[i]);
        }

        updateDots(0);
    }

    private void updateDots(int position) {
        for (int i = 0; i < dots.length; i++) {
            dots[i].setBackgroundResource(i == position ? R.drawable.dot_active : R.drawable.dot_inactive);
        }
    }

    private void startRoleSelection() {
        startActivity(new Intent(this, RoleSelectionActivity.class));
        finish();
    }

    private static class GuidePagerAdapter extends PagerAdapter {

        private List<GuidePage> pages;

        public GuidePagerAdapter(List<GuidePage> pages) {
            this.pages = pages;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            LayoutInflater inflater = LayoutInflater.from(container.getContext());
            View view = inflater.inflate(R.layout.item_guide_page, container, false);

            GuidePage page = pages.get(position);

            ImageView icon = view.findViewById(R.id.pageIcon);
            TextView title = view.findViewById(R.id.pageTitle);
            TextView description = view.findViewById(R.id.pageDescription);

            icon.setImageResource(page.iconRes);
            title.setText(page.title);
            description.setText(page.description);

            container.addView(view);
            return view;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View) object);
        }

        @Override
        public int getCount() {
            return pages.size();
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }
    }

    private static class GuidePage {
        String title;
        String description;
        int iconRes;

        public GuidePage(String title, String description, int iconRes) {
            this.title = title;
            this.description = description;
            this.iconRes = iconRes;
        }
    }
}
