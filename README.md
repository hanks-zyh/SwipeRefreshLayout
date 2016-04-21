# SwipeRefreshLayout
谷歌的下拉刷新，新的界面效果


# SwipeRefreshLayout 源码分析


关键词: Android SwipeRefreshLayout 下拉刷新

## 简介
[官方文档](http://developer.android.com/intl/zh-cn/reference/android/support/v4/widget/SwipeRefreshLayout.html) SwipeRefreshLayout 是一个下拉刷新控件，可以包裹一个任何可以滑动的内容，可以自动识别垂直滑动手势。
使用起来非常方便。

| | |
|:-:|:-:|
|![](http://img.blog.csdn.net/20150127120706062)|![](http://img.blog.csdn.net/20150127121649015)|

将需要下拉刷新的空间包裹起来
```xml
<android.support.v4.widget.SwipeRefreshLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <android.support.v7.widget.RecyclerView
        android:id="@+id/recyclerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"/>

</android.support.v4.widget.SwipeRefreshLayout>
```


设置刷新动画的触发回调
```java
/*
 * Sets up a SwipeRefreshLayout.OnRefreshListener that is invoked when the user
 * performs a swipe-to-refresh gesture.
 */
mySwipeRefreshLayout.setOnRefreshListener(
    new SwipeRefreshLayout.OnRefreshListener() {
        @Override
        public void onRefresh() {
            Log.i(LOG_TAG, "onRefresh called from SwipeRefreshLayout");

            // This method performs the actual data-refresh operation.
            // The method calls setRefreshing(false) when it's finished.
            myUpdateOperation();
        }
    }
);

```

通过 `setRefreshing(false)` 和 `setRefreshing(true)` 来手动调用刷新的动画（以前版本好像设置了不生效，现在修复了）。
**注意!** `onRefresh` 的回调只有在手势下拉的情况下才会触发，通过 `setRefreshing` 只能调用刷新的动画是否显示。
关于使用 SwipeRefreshLayout，查看[不一样的下拉刷新-----SwipeRefreshLayout](http://hanks.xyz/2015/01/27/%E4%B8%8D%E4%B8%80%E6%A0%B7%E7%9A%84%E4%B8%8B%E6%8B%89%E5%88%B7%E6%96%B0-----SwipeRefreshLayout/)

## SwipeRefreshLayout 源码分析

extends `ViewGroup` implements `NestedScrollingParent` `NestedScrollingChild`
```
java.lang.Object
   ↳	android.view.View
 	   ↳	android.view.ViewGroup
 	 	   ↳	android.support.v4.widget.SwipeRefreshLayout
```


其实就是一个自定义的 ViewGroup ，结合我们自己平时自定义 ViewGroup 的步骤：
1. 初始化变量
2. onMeasure
3. onLayout
4. 处理交互 dispatchTouchEvent onInterceptTouchEvent onTouchEvent
5. 暴露出公共接口供其他类调用

此外，实现了两个接口 `NestedScrollingParent` `NestedScrollingChild`。关于 `NestedScroll`机制，可以去 google。这里提供两篇文章:
- [NestedScrollingParent, NestedScrollingChild  详解](http://blog.csdn.net/chen930724/article/details/50307193)
- [Android NestedScrolling 实战](http://www.race604.com/android-nested-scrolling/)

简单总结一下，如果你的 View 实现 `NestedScrollingChild` 接口就可以支持嵌套滑动了（什么是嵌套滑动，就是滑动子View，父 View 也可以根据子 View 状态进行滑动，见 `CoordinatorLayout`）。同理，实现了 `NestedScrollingParent`接口就可以处理内部的子 View （实现了 NestedScrollingChild 的子 View）的滑动了。 NestedScrollingChildHelper 和 NestedScrollingParentHelper 是实现了对应的接口的类，可以帮助我们更简单的实现嵌套滑动（见上面的2篇文章）。

SwipeRefreshLayout 作为一个下拉刷新的动画，按理说只需要实现`NestedScrollingParent` 就行了，但是为了考虑到有其他可以滑动的组件嵌套 SwipeRefreshLayout（如 CoordinatorLayout ），所以也实现了`NestedScrollingChild`。Android 5.0 的大部分可以滑动的控件都支持了 NestScrolling 接口，最新的 Support V4 中也一样。


### 初始化变量


SwipeRefreshLayout 内部有 2 个 View，一个圆圈（mCircleView），一个内部可滚动的 View（mTarget）。除了 View，还包含一个 OnRefreshListener 接口，当刷新动画被触发时回调。

```java
/**
 * Constructor that is called when inflating SwipeRefreshLayout from XML.
 *
 * @param context
 * @param attrs
 */
public SwipeRefreshLayout(Context context, AttributeSet attrs) {
    super(context, attrs);

    // 系统默认的最小滑动距离
    mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

    // 系统默认的动画时长
    mMediumAnimationDuration = getResources().getInteger(
            android.R.integer.config_mediumAnimTime);

    setWillNotDraw(false);
    mDecelerateInterpolator = new DecelerateInterpolator(DECELERATE_INTERPOLATION_FACTOR);

    // 获取 xml 中定义的属性
    final TypedArray a = context.obtainStyledAttributes(attrs, LAYOUT_ATTRS);
    setEnabled(a.getBoolean(0, true));
    a.recycle();

    // 刷新的圆圈的大小
    final DisplayMetrics metrics = getResources().getDisplayMetrics();
    mCircleWidth = (int) (CIRCLE_DIAMETER * metrics.density);
    mCircleHeight = (int) (CIRCLE_DIAMETER * metrics.density);

    // 创建刷新动画的圆圈
    createProgressView();

    ViewCompat.setChildrenDrawingOrderEnabled(this, true);
    // the absolute offset has to take into account that the circle starts at an offset
    mSpinnerFinalOffset = DEFAULT_CIRCLE_TARGET * metrics.density;
    mTotalDragDistance = mSpinnerFinalOffset;

    // 通过 NestedScrolling 机制来控制 父 View 与 子View 的嵌套滚动
    mNestedScrollingParentHelper = new NestedScrollingParentHelper(this);
    mNestedScrollingChildHelper = new NestedScrollingChildHelper(this);
    setNestedScrollingEnabled(true);
}
```

// 创建刷新动画的圆圈
```java
private void createProgressView() {
    mCircleView = new CircleImageView(getContext(), CIRCLE_BG_LIGHT, CIRCLE_DIAMETER/2);
    mProgress = new MaterialProgressDrawable(getContext(), this);
    mProgress.setBackgroundColor(CIRCLE_BG_LIGHT);
    mCircleView.setImageDrawable(mProgress);
    mCircleView.setVisibility(View.GONE);
    addView(mCircleView);
}
```

### onMeasure

```java
@Override
public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (mTarget == null) {
        // 确定内部要滚动的View，如 RecycleView
        ensureTarget();
    }
    if (mTarget == null) {
        return;
    }
    mTarget.measure(MeasureSpec.makeMeasureSpec(
            getMeasuredWidth() - getPaddingLeft() - getPaddingRight(),
            MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(
            getMeasuredHeight() - getPaddingTop() - getPaddingBottom(), MeasureSpec.EXACTLY));

    mCircleView.measure(MeasureSpec.makeMeasureSpec(mCircleWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(mCircleHeight, MeasureSpec.EXACTLY));

    if (!mUsingCustomStart && !mOriginalOffsetCalculated) {
        mOriginalOffsetCalculated = true;
        mCurrentTargetOffsetTop = mOriginalOffsetTop = -mCircleView.getMeasuredHeight();
    }

    // 计算 mCircleView 在 ViewGroup 中的索引
    mCircleViewIndex = -1;
    // Get the index of the circleview.
    for (int index = 0; index < getChildCount(); index++) {
        if (getChildAt(index) == mCircleView) {
            mCircleViewIndex = index;
            break;
        }
    }
}
```


```java
private void ensureTarget() {
    // Don't bother getting the parent height if the parent hasn't been laid out yet.
    if (mTarget == null) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (!child.equals(mCircleView)) {
                mTarget = child;
                break;
            }
        }
    }
}
```

找到除了 mCircleView 的第一个子 View ，也就是内部可以滚动的 View， 并且赋值为 mTarget


### onLayout

```java
@Override
protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
   final int width = getMeasuredWidth();
   final int height = getMeasuredHeight();
   if (getChildCount() == 0) {
       return;
   }
   if (mTarget == null) {
       ensureTarget();
   }
   if (mTarget == null) {
       return;
   }
   final View child = mTarget;
   final int childLeft = getPaddingLeft();
   final int childTop = getPaddingTop();
   final int childWidth = width - getPaddingLeft() - getPaddingRight();
   final int childHeight = height - getPaddingTop() - getPaddingBottom();
   child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
   int circleWidth = mCircleView.getMeasuredWidth();
   int circleHeight = mCircleView.getMeasuredHeight();
   // 根据 mCurrentTargetOffsetTop 变量的值来设置 mCircleView 的位置
   mCircleView.layout((width / 2 - circleWidth / 2), mCurrentTargetOffsetTop,
           (width / 2 + circleWidth / 2), mCurrentTargetOffsetTop + circleHeight);
}
```


### 处理 Touch 事件

SwipeRefreshLayout 通过实现 `NestedScrollingParent` `NestedScrollingChild` 接口来分发触摸事件。

首先是 onInterceptTouchEvent，返回 true 表示拦截触摸事件。

```java


@Override
public boolean onInterceptTouchEvent(MotionEvent ev) {
    ensureTarget();

    final int action = MotionEventCompat.getActionMasked(ev);


    if (mReturningToStart && action == MotionEvent.ACTION_DOWN) {
        mReturningToStart = false;
    }

    // 空间可用 || 刷新事件刚结束正在恢复初始状态时 || 子 View 可滚动 || 正在刷新 ||
    if (!isEnabled() || mReturningToStart || canChildScrollUp()
            || mRefreshing || mNestedScrollInProgress) {
        // Fail fast if we're not in a state where a swipe is possible
        return false;
    }

    switch (action) {
        case MotionEvent.ACTION_DOWN:
            // 记录手指按下的位置，为了判断是否开始滑动
            setTargetOffsetTopAndBottom(mOriginalOffsetTop - mCircleView.getTop(), true);
            mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
            mIsBeingDragged = false;
            final float initialDownY = getMotionEventY(ev, mActivePointerId);
            if (initialDownY == -1) {
                return false;
            }
            mInitialDownY = initialDownY;
            break;

        case MotionEvent.ACTION_MOVE:
            if (mActivePointerId == INVALID_POINTER) {
                Log.e(LOG_TAG, "Got ACTION_MOVE event but don't have an active pointer id.");
                return false;
            }

            final float y = getMotionEventY(ev, mActivePointerId);
            if (y == -1) {
                return false;
            }
            // 判断当拖动距离大于最小距离时设置 mIsBeingDragged = true;
            final float yDiff = y - mInitialDownY;
            if (yDiff > mTouchSlop && !mIsBeingDragged) {
                mInitialMotionY = mInitialDownY + mTouchSlop;
                mIsBeingDragged = true;
                mProgress.setAlpha(STARTING_PROGRESS_ALPHA);
            }
            break;

        case MotionEventCompat.ACTION_POINTER_UP:
            onSecondaryPointerUp(ev);
            break;

        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
            mIsBeingDragged = false;
            mActivePointerId = INVALID_POINTER;
            break;
    }

    return mIsBeingDragged;
}

```
可以看到源码也就是进行简单处理，DOWN 的时候记录一下位置，MOVE 时判断移动的距离，返回值 mIsBeingDragged 为 true 时， 即 onInterceptTouchEvent 返回true，SwipeRefreshLayout 拦截触摸事件，不分发给 mTarget，然后把 MotionEvent 传给 onTouchEvent 方法。其中有一个判断子View的是否还可以滑动的方法 `canChildScrollUp`。

```java
/**
 * @return Whether it is possible for the child view of this layout to
 *         scroll up. Override this if the child view is a custom view.
 */
public boolean canChildScrollUp() {
    if (android.os.Build.VERSION.SDK_INT < 14) {
        // 判断 AbsListView 的子类 ListView 或者 GridView 等
        if (mTarget instanceof AbsListView) {
            final AbsListView absListView = (AbsListView) mTarget;
            return absListView.getChildCount() > 0
                    && (absListView.getFirstVisiblePosition() > 0 || absListView.getChildAt(0)
                            .getTop() < absListView.getPaddingTop());
        } else {
            return ViewCompat.canScrollVertically(mTarget, -1) || mTarget.getScrollY() > 0;
        }
    } else {
        return ViewCompat.canScrollVertically(mTarget, -1);
    }
}

```


```java

@Override
public boolean onTouchEvent(MotionEvent ev) {
    final int action = MotionEventCompat.getActionMasked(ev);
    int pointerIndex = -1;

    if (mReturningToStart && action == MotionEvent.ACTION_DOWN) {
        mReturningToStart = false;
    }

    if (!isEnabled() || mReturningToStart || canChildScrollUp() || mNestedScrollInProgress) {
        // Fail fast if we're not in a state where a swipe is possible
        return false;
    }

    switch (action) {
        case MotionEvent.ACTION_DOWN:
            mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
            mIsBeingDragged = false;
            break;

        case MotionEvent.ACTION_MOVE: {
            pointerIndex = MotionEventCompat.findPointerIndex(ev, mActivePointerId);
            if (pointerIndex < 0) {
                Log.e(LOG_TAG, "Got ACTION_MOVE event but have an invalid active pointer id.");
                return false;
            }

            final float y = MotionEventCompat.getY(ev, pointerIndex);
            final float overscrollTop = (y - mInitialMotionY) * DRAG_RATE;
            if (mIsBeingDragged) {
                if (overscrollTop > 0) {
                    // 正在拖动状态，更新圆圈的位置
                    moveSpinner(overscrollTop);
                } else {
                    return false;
                }
            }
            break;
        }
        // 处理多指触控
        case MotionEventCompat.ACTION_POINTER_DOWN: {
            pointerIndex = MotionEventCompat.getActionIndex(ev);
            if (pointerIndex < 0) {
                Log.e(LOG_TAG, "Got ACTION_POINTER_DOWN event but have an invalid action index.");
                return false;
            }
            mActivePointerId = MotionEventCompat.getPointerId(ev, pointerIndex);
            break;
        }

        case MotionEventCompat.ACTION_POINTER_UP:
            onSecondaryPointerUp(ev);
            break;

        case MotionEvent.ACTION_UP: {
            pointerIndex = MotionEventCompat.findPointerIndex(ev, mActivePointerId);
            if (pointerIndex < 0) {
                Log.e(LOG_TAG, "Got ACTION_UP event but don't have an active pointer id.");
                return false;
            }

            final float y = MotionEventCompat.getY(ev, pointerIndex);
            final float overscrollTop = (y - mInitialMotionY) * DRAG_RATE;
            mIsBeingDragged = false;
            // 手指松开，将圆圈移动到正确的位置
            finishSpinner(overscrollTop);
            mActivePointerId = INVALID_POINTER;
            return false;
        }
        case MotionEvent.ACTION_CANCEL:
            return false;
    }

    return true;
}

```


```java
// 手指下拉过程中触发的圆圈的变化过程，透明度变化，渐渐出现箭头，大小的变化
private void moveSpinner(float overscrollTop) {
    mProgress.showArrow(true);
    float originalDragPercent = overscrollTop / mTotalDragDistance;

    float dragPercent = Math.min(1f, Math.abs(originalDragPercent));
    float adjustedPercent = (float) Math.max(dragPercent - .4, 0) * 5 / 3;
    float extraOS = Math.abs(overscrollTop) - mTotalDragDistance;
    float slingshotDist = mUsingCustomStart ? mSpinnerFinalOffset - mOriginalOffsetTop
            : mSpinnerFinalOffset;
    float tensionSlingshotPercent = Math.max(0, Math.min(extraOS, slingshotDist * 2)
            / slingshotDist);
    float tensionPercent = (float) ((tensionSlingshotPercent / 4) - Math.pow(
            (tensionSlingshotPercent / 4), 2)) * 2f;
    float extraMove = (slingshotDist) * tensionPercent * 2;

    int targetY = mOriginalOffsetTop + (int) ((slingshotDist * dragPercent) + extraMove);
    // where 1.0f is a full circle
    if (mCircleView.getVisibility() != View.VISIBLE) {
        mCircleView.setVisibility(View.VISIBLE);
    }
    if (!mScale) {
        ViewCompat.setScaleX(mCircleView, 1f);
        ViewCompat.setScaleY(mCircleView, 1f);
    }

    if (mScale) {
        setAnimationProgress(Math.min(1f, overscrollTop / mTotalDragDistance));
    }
    if (overscrollTop < mTotalDragDistance) {
        if (mProgress.getAlpha() > STARTING_PROGRESS_ALPHA
                && !isAnimationRunning(mAlphaStartAnimation)) {
            // Animate the alpha
            startProgressAlphaStartAnimation();
        }
    } else {
        if (mProgress.getAlpha() < MAX_ALPHA && !isAnimationRunning(mAlphaMaxAnimation)) {
            // Animate the alpha
            startProgressAlphaMaxAnimation();
        }
    }
    float strokeStart = adjustedPercent * .8f;
    mProgress.setStartEndTrim(0f, Math.min(MAX_PROGRESS_ANGLE, strokeStart));
    mProgress.setArrowScale(Math.min(1f, adjustedPercent));

    float rotation = (-0.25f + .4f * adjustedPercent + tensionPercent * 2) * .5f;
    mProgress.setProgressRotation(rotation);
    setTargetOffsetTopAndBottom(targetY - mCurrentTargetOffsetTop, true /* requires update */);
}
```

```java
private void finishSpinner(float overscrollTop) {
    if (overscrollTop > mTotalDragDistance) {
        //移动距离超过了刷新的临界值，触发刷新动画
        setRefreshing(true, true /* notify */);
    } else {
        // 取消刷新的圆圈，将圆圈移动到初始位置
        mRefreshing = false;
        mProgress.setStartEndTrim(0f, 0f);
        Animation.AnimationListener listener = null;
        if (!mScale) {
            listener = new Animation.AnimationListener() {

                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    if (!mScale) {
                        startScaleDownAnimation(null);
                    }
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }

            };
        }
        animateOffsetToStartPosition(mCurrentTargetOffsetTop, listener);
        mProgress.showArrow(false);
    }
}

```

可以看到调用 setRefresh(true,true) 方法触发刷新动画并进行回调，但是这个方法是 private 的。前面提到我们自己调用 setRefresh(true) 只能产生动画，而不能回调刷新函数，那么我们就可以用反射调用 2 个参数的 setRefresh 函数。 或者手动调 setRefreshing(true)+ OnRefreshListener.onRefresh 方法。


### setRefresh

```java
/**
  * 改变刷新动画的的圆圈刷新状态。Notify the widget that refresh state has changed. Do not call this when
  * refresh is triggered by a swipe gesture.
  *
  * @param refreshing 是否显示刷新的圆圈
  */
 public void setRefreshing(boolean refreshing) {
     if (refreshing && mRefreshing != refreshing) {
         // scale and show
         mRefreshing = refreshing;
         int endTarget = 0;
         if (!mUsingCustomStart) {
             endTarget = (int) (mSpinnerFinalOffset + mOriginalOffsetTop);
         } else {
             endTarget = (int) mSpinnerFinalOffset;
         }
         setTargetOffsetTopAndBottom(endTarget - mCurrentTargetOffsetTop,
                 true /* requires update */);
         mNotify = false;
         startScaleUpAnimation(mRefreshListener);
     } else {
         setRefreshing(refreshing, false /* notify */);
     }
 }
```

startScaleUpAnimation 开启一个动画，然后在动画结束后回调 onRefresh 方法。
```java
private Animation.AnimationListener mRefreshListener = new Animation.AnimationListener() {
   @Override
   public void onAnimationStart(Animation animation) {
   }

   @Override
   public void onAnimationRepeat(Animation animation) {
   }

   @Override
   public void onAnimationEnd(Animation animation) {
       if (mRefreshing) {
           // Make sure the progress view is fully visible
           mProgress.setAlpha(MAX_ALPHA);
           mProgress.start();
           if (mNotify) {
               if (mListener != null) {
                   // 回调 listener 的 onRefresh 方法
                   mListener.onRefresh();
               }
           }
           mCurrentTargetOffsetTop = mCircleView.getTop();
       } else {
           reset();
       }
   }
};

```
## 总结

分析 SwipeRefreshLayout 的流程就是按照平时我们自定义 ViewGroup 的流程，但是其中有好多需要我们借鉴的地方，处理滑动冲突其实就是使用 NestedScroll 机制，还有多点触控的处理，onMeasure 中减去了 padding。
