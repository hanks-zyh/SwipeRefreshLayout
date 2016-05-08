



## 简介
[官方文档](http://developer.android.com/intl/zh-cn/reference/android/support/v4/widget/SwipeRefreshLayout.html)

`SwipeRefreshLayout` 是一个下拉刷新控件，几乎可以包裹一个任何可以滚动的内容（ListView GridView ScrollView RecyclerView），可以自动识别垂直滚动手势。使用起来非常方便。

| | |
|:-:|:-:|
|![](http://img.blog.csdn.net/20150127120706062)|![](http://img.blog.csdn.net/20150127121649015)|

 1.将需要下拉刷新的空间包裹起来

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


 2.设置刷新动画的触发回调

```java
//设置下拉出现小圆圈是否是缩放出现，出现的位置，最大的下拉位置
mySwipeRefreshLayout.setProgressViewOffset(true, 50, 200);

//设置下拉圆圈的大小，两个值 LARGE， DEFAULT
mySwipeRefreshLayout.setSize(SwipeRefreshLayout.LARGE);

// 设置下拉圆圈上的颜色，蓝色、绿色、橙色、红色
mySwipeRefreshLayout.setColorSchemeResources(
    android.R.color.holo_blue_bright,
    android.R.color.holo_green_light,
    android.R.color.holo_orange_light,
    android.R.color.holo_red_light);

// 设定下拉圆圈的背景
mSwipeLayout.setProgressBackgroundColor(R.color.red);

/*
 * 设置手势下拉刷新的监听
 */
mySwipeRefreshLayout.setOnRefreshListener(
    new SwipeRefreshLayout.OnRefreshListener() {
        @Override
        public void onRefresh() {
            // 刷新动画开始后回调到此方法
        }
    }
);

```

通过 `setRefreshing(false)` 和 `setRefreshing(true)` 来手动调用刷新的动画。

>`onRefresh` 的回调只有在手势下拉的情况下才会触发，通过 `setRefreshing` 只能调用刷新的动画是否显示。
> SwipeRefreshLayout 也可放在 CoordinatorLayout 内共同处理滑动冲突，有兴趣可以尝试。


## SwipeRefreshLayout 源码分析

> 本文基于 v4 版本 `23.2.0`

extends `ViewGroup` implements `NestedScrollingParent` `NestedScrollingChild`
```
java.lang.Object
   ↳	android.view.View
 	   ↳	android.view.ViewGroup
 	 	   ↳	android.support.v4.widget.SwipeRefreshLayout
```
SwipeRefreshLayout 的分析分为两个部分：自定义 ViewGroup 的部分，处理和 子View 的嵌套滚动部分。



### SwipeRefreshLayout extends ViewGroup

其实就是一个自定义的 ViewGroup ，结合我们自己平时自定义 ViewGroup 的步骤：

1. 初始化变量
2. onMeasure
3. onLayout
4. 处理交互 （`dispatchTouchEvent` `onInterceptTouchEvent` `onTouchEvent`）

接下来就按照上面的步骤进行分析。



#### 1.初始化变量


`SwipeRefreshLayout` 内部有 2 个 View，一个`圆圈（mCircleView）`，一个内部可滚动的` View（mTarget）`。除了 View，还包含一个 `OnRefreshListener` 接口，当刷新动画被触发时回调。


 ![图片](https://dn-coding-net-production-pp.qbox.me/8e02212d-b364-4df8-bfaa-47f3084f89e7.png)


```java
/**
 * Constructor that is called when inflating SwipeRefreshLayout from XML.
 *
 * @param context
 * @param attrs
 */
public SwipeRefreshLayout(Context context, AttributeSet attrs) {
    super(context, attrs);

    // 系统默认的最小滚动距离
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

    // 刷新的圆圈的大小，单位转换成 sp
    final DisplayMetrics metrics = getResources().getDisplayMetrics();
    mCircleWidth = (int) (CIRCLE_DIAMETER * metrics.density);
    mCircleHeight = (int) (CIRCLE_DIAMETER * metrics.density);

    // 创建刷新动画的圆圈
    createProgressView();

    ViewCompat.setChildrenDrawingOrderEnabled(this, true);
    // the absolute offset has to take into account that the circle starts at an offset
    mSpinnerFinalOffset = DEFAULT_CIRCLE_TARGET * metrics.density;
    // 刷新动画的临界距离值
    mTotalDragDistance = mSpinnerFinalOffset;

    // 通过 NestedScrolling 机制来处理嵌套滚动
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

初始化的时候创建一个出来一个 View （下拉刷新的圆圈）。可以看出使用背景圆圈是 v4 包里提供的 `CircleImageView` 控件，中间的是 `MaterialProgressDrawable` 进度条。
另一个 View 是在 xml 中包含的可滚动视图。

#### 2.onMeasure

onMeasure 确定子视图的大小。

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

    // 测量子 View （mTarget）
    mTarget.measure(MeasureSpec.makeMeasureSpec(
            getMeasuredWidth() - getPaddingLeft() - getPaddingRight(),
            MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(
            getMeasuredHeight() - getPaddingTop() - getPaddingBottom(), MeasureSpec.EXACTLY));

    // 测量刷新的圆圈 mCircleView
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

这个步骤确定了 mCircleView 和 SwipeRefreshLayout 的子视图的大小。


#### 3.onLayout

onLayout 主要负责确定各个子视图的位置。

```java
@Override
protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
   // 获取 SwipeRefreshLayout 的宽高
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
   // 考虑到给控件设置 padding，去除 padding 的距离
   final View child = mTarget;
   final int childLeft = getPaddingLeft();
   final int childTop = getPaddingTop();
   final int childWidth = width - getPaddingLeft() - getPaddingRight();
   final int childHeight = height - getPaddingTop() - getPaddingBottom();
   // 设置 mTarget 的位置
   child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
   int circleWidth = mCircleView.getMeasuredWidth();
   int circleHeight = mCircleView.getMeasuredHeight();
   // 根据 mCurrentTargetOffsetTop 变量的值来设置 mCircleView 的位置
   mCircleView.layout((width / 2 - circleWidth / 2), mCurrentTargetOffsetTop,
           (width / 2 + circleWidth / 2), mCurrentTargetOffsetTop + circleHeight);
}
```
 ![图片](https://dn-coding-net-production-pp.qbox.me/8df6d458-700b-4ec5-b731-c6b8c34cdddc.png)

在 onLayout 中放置了 mCircleView 的位置，注意 顶部位置是 mCurrentTargetOffsetTop ，mCurrentTargetOffsetTop 初始距离是`-mCircleView.getMeasuredHeight()`，所以是在 SwipeRefreshLayout 外。


> 经过以上几个步骤，SwipeRefreshLayout 创建了子视图，确定他们的大小、位置，现在所有视图可以显示在界面了。

### 处理与子视图的滚动交互

下拉刷新控件的主要功能是当子视图下拉到最顶部时，继续下拉可以出现刷新动画。而子视图可以滚动时需要将所有滚动事件都交给子视图。借助 Android 提供的 NestedScrolling 机制，使得 SwipeRefreshLayout 很轻松的解决了与子视图的滚动冲突问题。
SwipeRefreshLayout 通过实现 `NestedScrollingParent` 和 `NestedScrollingChild` 接口来处理滚动冲突。SwipeRefreshLayout 作为 Parent 嵌套一个可以滚动的子视图，那么就需要了解一下 NestedScrollingParent 接口


```java
/**
 当你希望自己的自定义布局支持嵌套子视图并且处理滚动操作，就可以实现该接口。
 实现这个接口后可以创建一个 NestedScrollingParentHelper 字段，使用它来帮助你处理大部分的方法。
 处理嵌套的滚动时应该使用  `ViewCompat`，`ViewGroupCompat`或`ViewParentCompat` 中的方法来处理，这是一些兼容库，
 他们保证 Android 5.0之前的兼容性垫片的静态方法，这样可以兼容 Android 5.0 之前的版本。
 */
public interface NestedScrollingParent {
    /**
     * 当子视图调用 startNestedScroll(View, int) 后调用该方法。返回 true 表示响应子视图的滚动。
     * 实现这个方法来声明支持嵌套滚动，如果返回 true，那么这个视图将要配合子视图嵌套滚动。当嵌套滚动结束时会调用到 onStopNestedScroll(View)。
     *
     * @param child 可滚动的子视图
     * @param target NestedScrollingParent 的直接可滚动的视图，一般情况就是 child
     * @param nestedScrollAxes 包含 ViewCompat#SCROLL_AXIS_HORIZONTAL, ViewCompat#SCROLL_AXIS_VERTICAL 或者两个值都有。
     * @return 返回 true 表示响应子视图的滚动。
     */
    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes);

    /**
     * 如果 onStartNestedScroll 返回 true ，然后走该方法，这个方法里可以做一些初始化。
     */
    public void onNestedScrollAccepted(View child, View target, int nestedScrollAxes);


    /**
     * 子视图开始滚动前会调用这个方法。这时候父布局（也就是当前的 NestedScrollingParent 的实现类）可以通过这个方法来配合子视图同时处理滚动事件。
     *
     * @param target 滚动的子视图
     * @param dx 绝对值为手指在x方向滚动的距离，dx<0 表示手指在屏幕向右滚动
     * @param dy 绝对值为手指在y方向滚动的距离，dy<0 表示手指在屏幕向下滚动
     * @param consumed 一个数组，值用来表示父布局消耗了多少距离，未消耗前为[0,0], 如果父布局想处理滚动事件，就可以在这个方法的实现中为consumed[0]，consumed[1]赋值。
     *                 分别表示x和y方向消耗的距离。如父布局想在竖直方向（y）完全拦截子视图，那么让 consumed[1] = dy，就把手指产生的触摸事件给拦截了，子视图便响应不到触摸事件了 。
     */
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed);


  /**
     * 这个方法表示子视图正在滚动，并且把滚动距离回调用到该方法，前提是 onStartNestedScroll 返回了 true。
     * <p>Both the consumed and unconsumed portions of the scroll distance are reported to the
     * ViewParent. An implementation may choose to use the consumed portion to match or chase scroll
     * position of multiple child elements, for example. The unconsumed portion may be used to
     * allow continuous dragging of multiple scrolling or draggable elements, such as scrolling
     * a list within a vertical drawer where the drawer begins dragging once the edge of inner
     * scrolling content is reached.</p>
     *
     * @param target 滚动的子视图
     * @param dxConsumed 手指产生的触摸距离中，子视图消耗的x方向的距离
     * @param dyConsumed 手指产生的触摸距离中，子视图消耗的y方向的距离 ，如果 onNestedPreScroll 中 dy = 20， consumed[0] = 8，那么 dy = 12
      * @param dxUnconsumed 手指产生的触摸距离中，未被子视图消耗的x方向的距离
     * @param dyUnconsumed 手指产生的触摸距离中，未被子视图消耗的y方向的距离
     */
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed,int dxUnconsumed, int dyUnconsumed);



    /**
     * 响应嵌套滚动结束
     *
     * 当一个嵌套滚动结束后（如MotionEvent#ACTION_UP， MotionEvent#ACTION_CANCEL）会调用该方法，在这里可有做一些收尾工作，比如变量重置
     */
    public void onStopNestedScroll(View target);


    /**
     * 手指在屏幕快速滑触发Fling前回调，如果前面 onNestedPreScroll 中父布局消耗了事件，那么这个也会被触发
     * 返回true表示父布局完全处理 fling 事件
     *
     * @param target 滚动的子视图
     * @param velocityX x方向的速度（px/s）
     * @param velocityY y方向的速度
     * @return true if this parent consumed the fling ahead of the target view
     */
    public boolean onNestedPreFling(View target, float velocityX, float velocityY);

    /**
     * 子视图fling 时回调，父布局可以选择监听子视图的 fling。
     * true 表示父布局处理 fling，false表示父布局监听子视图的fling
     *
     * @param target View that initiated the nested scroll
     * @param velocityX Horizontal velocity in pixels per second
     * @param velocityY Vertical velocity in pixels per second
     * @param consumed true 表示子视图处理了fling

     */
    public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed);



    /**
     * 返回当前 NestedScrollingParent 的滚动方向，
     *
     * @return
     * @see ViewCompat#SCROLL_AXIS_HORIZONTAL
     * @see ViewCompat#SCROLL_AXIS_VERTICAL
     * @see ViewCompat#SCROLL_AXIS_NONE
     */
    public int getNestedScrollAxes();
}

```

看一下 SwipeRefreshLayout 实现 NestedScrollingParent 的相关方法
```java
// NestedScrollingParent

// 子 View （NestedScrollingChild）开始滚动前回调此方法,返回 true 表示接 Parent 收嵌套滚动，然后调用 onNestedScrollAccepted
// 具体可以看 NestedScrollingChildHelper 的源码
@Override
public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
    // 子 View 回调，判断是否开始嵌套滚动 ，
    return isEnabled() && !mReturningToStart && !mRefreshing
            && (nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
}

@Override
 public void onNestedScrollAccepted(View child, View target, int axes) {
     // Reset the counter of how much leftover scroll needs to be consumed.
     mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes);

     // ...省略代码
 }
```
SwipeRefreshLayout 只接受竖直方向（Y轴）的滚动，并且在刷新动画进行中不接受滚动。

```java
// NestedScrollingChild 在滚动的时候会触发， 看父类消耗了多少距离
//   * @param dx x 轴滚动的距离
//   * @param dy y 轴滚动的距离
//   * @param consumed 代表 父 View 消费的滚动距离
//
@Override
public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {

    // dy > 0 表示手指在屏幕向上移动
    //  mTotalUnconsumed > 0 表示子视图也处理可滚动状态
    // 现在表示
    if (dy > 0 && mTotalUnconsumed > 0) {
        // 手指向上移动的距离已经达到圆圈的初始位置
        if (dy > mTotalUnconsumed) {
            consumed[1] = dy - (int) mTotalUnconsumed; // parent 消费的 y 轴的距离
            mTotalUnconsumed = 0;
        } else {
            mTotalUnconsumed -= dy; // 消费的 y 轴的距离
            consumed[1] = dy;
        }
        // 出现动画圆圈，并向上移动
        moveSpinner(mTotalUnconsumed);
    }

    // ... 省略代码
}


// onStartNestedScroll 返回 true 才会调用此方法。此方法表示子View将滚动事件分发到父 View（SwipeRefreshLayout）
//  @param target The descendent view controlling the nested scroll
//  @param dxConsumed Horizontal scroll distance in pixels already consumed by target
//  @param dyConsumed Vertical scroll distance in pixels already consumed by target
//  @param dxUnconsumed Horizontal scroll distance in pixels not consumed by target
//  @param dyUnconsumed Vertical scroll distance in pixels not consumed by target
@Override
public void onNestedScroll(final View target, final int dxConsumed, final int dyConsumed,
        final int dxUnconsumed, final int dyUnconsumed) {
    // ... 省略代码

    // This is a bit of a hack. Nested scrolling works from the bottom up, and as we are
    // sometimes between two nested scrolling views, we need a way to be able to know when any
    // nested scrolling parent has stopped handling events. We do that by using the
    // 'offset in window 'functionality to see if we have been moved from the event.
    // This is a decent indication of whether we should take over the event stream or not.
    // 手指在屏幕上向下滚动，并且子视图不可以滚动
    final int dy = dyUnconsumed + mParentOffsetInWindow[1];
    if (dy < 0 && !canChildScrollUp()) {
        mTotalUnconsumed += Math.abs(dy);
        moveSpinner(mTotalUnconsumed);
    }
}
```
SwipeRefreshLayout 通过 NestedScrollingParent 接口完成了处理子视图的滚动的冲突，中间我隐藏了一些 SwipeRefreshLayout作为 child 的相关代码，这种情况是为了兼容将 SwipeRefreshLayout 作为子视图放在知识嵌套滚动的父布局的情况，这里不做深入讨论。但是下拉刷新需要判断手指在屏幕的状态来进行一个刷新的动画，所以我们还需要处理触摸事件，判断手指在屏幕中的状态。


首先是 onInterceptTouchEvent，返回 true 表示拦截触摸事件。

```java


@Override
public boolean onInterceptTouchEvent(MotionEvent ev) {
    ensureTarget();

    final int action = MotionEventCompat.getActionMasked(ev);

    // 手指按下时恢复状态
    if (mReturningToStart && action == MotionEvent.ACTION_DOWN) {
        mReturningToStart = false;
    }

    // 控件可用 || 刷新事件刚结束正在恢复初始状态时 || 子 View 可滚动 || 正在刷新 || 父 View 正在滚动
    if (!isEnabled() || mReturningToStart || canChildScrollUp()
            || mRefreshing || mNestedScrollInProgress) {
        // Fail fast if we're not in a state where a swipe is possible
        return false;
    }

    switch (action) {
        case MotionEvent.ACTION_DOWN:
            setTargetOffsetTopAndBottom(mOriginalOffsetTop - mCircleView.getTop(), true);
            mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
            mIsBeingDragged = false;
            // 记录手指按下的位置，为了判断是否开始滚动
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
                // 正在拖动状态，更新圆圈的 progressbar 的 alpha 值
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
可以看到源码也就是进行简单处理，DOWN 的时候记录一下位置，MOVE 时判断移动的距离，返回值 mIsBeingDragged 为 true 时， 即 onInterceptTouchEvent 返回true，SwipeRefreshLayout 拦截触摸事件，不分发给 mTarget，然后把 MotionEvent 传给 onTouchEvent 方法。其中有一个判断子View的是否还可以滚动的方法 `canChildScrollUp`。

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

当SwipeRefreshLayout 拦截了触摸事件之后（ mIsBeingDragged 为 true ），将 MotionEvent 交给 onTouchEvent 处理。
```java

@Override
public boolean onTouchEvent(MotionEvent ev) {

    // ... 省略代码
    switch (action) {
        case MotionEvent.ACTION_DOWN:
            // 获取第一个按下的手指
            mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
            mIsBeingDragged = false;
            break;

        case MotionEvent.ACTION_MOVE: {
            // 处理多指触控
            pointerIndex = MotionEventCompat.findPointerIndex(ev, mActivePointerId);

            // ... 省略代码

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

        // ... 省略代码
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
        // ... 省略代码
    }

    return true;
}

```

在手指滚动过程中通过判断 mIsBeingDragged 来移动刷新的圆圈（对应的是 moveSpinner ），手指松开将圆圈移动到正确位置（初始位置或者刷新动画的位置，对应的是 finishSpinner 方法）。

```java
// 手指下拉过程中触发的圆圈的变化过程，透明度变化，渐渐出现箭头，大小的变化
private void moveSpinner(float overscrollTop) {

    // 设置为有箭头的 progress
    mProgress.showArrow(true);

    // 进度转化成百分比
    float originalDragPercent = overscrollTop / mTotalDragDistance;

    // 避免百分比超过 100%
    float dragPercent = Math.min(1f, Math.abs(originalDragPercent));
    // 调整拖动百分比，造成视差效果
    float adjustedPercent = (float) Math.max(dragPercent - .4, 0) * 5 / 3;
    //
    float extraOS = Math.abs(overscrollTop) - mTotalDragDistance;

    // 这里mUsingCustomStart 为 true 代表用户自定义了起始出现的坐标
    float slingshotDist = mUsingCustomStart ? mSpinnerFinalOffset - mOriginalOffsetTop
            : mSpinnerFinalOffset;

    // 弹性系数
    float tensionSlingshotPercent = Math.max(0, Math.min(extraOS, slingshotDist * 2)
            / slingshotDist);
    float tensionPercent = (float) ((tensionSlingshotPercent / 4) - Math.pow(
            (tensionSlingshotPercent / 4), 2)) * 2f;
    float extraMove = (slingshotDist) * tensionPercent * 2;

    // 因为有弹性系数，不同的手指滚动距离不同于view的移动距离
    int targetY = mOriginalOffsetTop + (int) ((slingshotDist * dragPercent) + extraMove);

    // where 1.0f is a full circle
    if (mCircleView.getVisibility() != View.VISIBLE) {
        mCircleView.setVisibility(View.VISIBLE);
    }
    // 设置的是否有缩放
    if (!mScale) {
        ViewCompat.setScaleX(mCircleView, 1f);
        ViewCompat.setScaleY(mCircleView, 1f);
    }
    // 设置缩放进度
    if (mScale) {
        setAnimationProgress(Math.min(1f, overscrollTop / mTotalDragDistance));
    }
    // 移动距离未达到最大距离
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
    // 出现的进度，裁剪 mProgress
    float strokeStart = adjustedPercent * .8f;
    mProgress.setStartEndTrim(0f, Math.min(MAX_PROGRESS_ANGLE, strokeStart));
    mProgress.setArrowScale(Math.min(1f, adjustedPercent));

    // 旋转
    float rotation = (-0.25f + .4f * adjustedPercent + tensionPercent * 2) * .5f;
    mProgress.setProgressRotation(rotation);
    setTargetOffsetTopAndBottom(targetY - mCurrentTargetOffsetTop, true /* requires update */);
}
```

刷新圆圈的移动过程也是有好几种状态，看上面的注释基本上就比较清楚了。

```java
private void finishSpinner(float overscrollTop) {
    if (overscrollTop > mTotalDragDistance) {
        //移动距离超过了刷新的临界值，触发刷新动画
        setRefreshing(true, true /* notify */);
    } else {
        // 取消刷新的圆圈，将圆圈移动到初始位置
        mRefreshing = false;
        mProgress.setStartEndTrim(0f, 0f);
        // ...省略代码

        // 移动到初始位置
        animateOffsetToStartPosition(mCurrentTargetOffsetTop, listener);
        // 设置没有箭头
        mProgress.showArrow(false)
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
   // .. 省略代码
   @Override
   public void onAnimationEnd(Animation animation) {
       if (mRefreshing) {
           mProgress.setAlpha(MAX_ALPHA); //确保刷新圆圈中间的进度条是完全不透明了
           mProgress.start();
           if (mNotify) { // 当 mNotify 为 true 时才会回调 onRefresh
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

可以看到刷新动画结束后，通过 mNotify 的值判断是否回调 listener 的 onRefresh 方法。前面提到的手动调用 mSwipeLayout.setRefreshing(true) 为什么不会回调 onRefresh 的原因就在这。

## 总结

分析 SwipeRefreshLayout 的流程就是按照平时我们自定义 `ViewGroup` 的流程，但是其中也有好多需要我们借鉴的地方，如何使用 NestedScrolling相关机制 ，多点触控的处理，onMeasure 中减去了 padding，如何判断子 View 是否可滚动，如何确定 ViewGroup 中某一个 View 的索引等。
此外，一个好的下拉刷新框架不仅仅要兼容各种滚动的子控件，还要考虑自己要兼容 NestedScrollingChild 的情况，比如放到 CooCoordinatorLayout 的情况，目前大多数开源的下拉刷新好像都没有达到这个要求，一般都是只考虑了内部嵌套滚动子视图的情况，没有考虑自己作为滚动子视图的情况。
