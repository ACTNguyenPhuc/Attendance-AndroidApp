package com.example.attendanceapplication.adapters;

import android.graphics.Canvas;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.models.Shift;

/**
 * Swipe action cho danh sách ca học của giáo viên.
 *
 * <p>Vuốt thẻ ca học sang trái sẽ dịch chuyển phần foreground (thẻ) để lộ ra
 * panel "Dời ca" nằm phía sau; vuốt qua ngưỡng sẽ kích hoạt dialog dời ca.
 * Chỉ những ca {@link ShiftListAdapter#isReschedulable(Shift)} mới cho phép vuốt;
 * các ca đã mở điểm danh / đã kết thúc / đã hủy bị khóa (không vuốt được).
 *
 * <p>Việc dịch chuyển dùng {@link #getDefaultUIUtil()} trên view foreground nên
 * panel phía sau được giữ nguyên (lộ dần) thay vì kéo cả hàng đi.
 */
public class ShiftSwipeCallback extends ItemTouchHelper.SimpleCallback {

    private final ShiftListAdapter adapter;

    public ShiftSwipeCallback(ShiftListAdapter adapter) {
        super(0, ItemTouchHelper.LEFT);
        this.adapter = adapter;
    }

    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                @NonNull RecyclerView.ViewHolder viewHolder) {
        Shift shift = adapter.getShiftAt(viewHolder.getBindingAdapterPosition());
        // Khóa vuốt với ca không được phép dời.
        if (!ShiftListAdapter.isReschedulable(shift)) return 0;
        return super.getMovementFlags(recyclerView, viewHolder);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target) {
        return false; // không hỗ trợ kéo sắp xếp
    }

    // Cần vuốt rõ ràng (~40% bề rộng) mới kích hoạt, tránh chạm nhầm.
    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        return 0.4f;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        int pos = viewHolder.getBindingAdapterPosition();
        Shift shift = adapter.getShiftAt(pos);
        // Trả thẻ về vị trí cũ (mở dialog thay vì xóa hàng).
        if (pos != RecyclerView.NO_POSITION) adapter.notifyItemChanged(pos);
        adapter.notifyReschedule(shift);
    }

    @Override
    public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
        if (viewHolder instanceof ShiftListAdapter.ViewHolder) {
            getDefaultUIUtil().onSelected(((ShiftListAdapter.ViewHolder) viewHolder).foreground);
        }
    }

    @Override
    public void clearView(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder) {
        if (viewHolder instanceof ShiftListAdapter.ViewHolder) {
            getDefaultUIUtil().clearView(((ShiftListAdapter.ViewHolder) viewHolder).foreground);
        }
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            float dX, float dY, int actionState, boolean isCurrentlyActive) {
        if (viewHolder instanceof ShiftListAdapter.ViewHolder) {
            // Chỉ cho lộ panel (vuốt trái); chặn vuốt phải để panel không bị che lệch.
            float clampedX = Math.min(0f, dX);
            getDefaultUIUtil().onDraw(c, recyclerView,
                    ((ShiftListAdapter.ViewHolder) viewHolder).foreground,
                    clampedX, dY, actionState, isCurrentlyActive);
        }
    }

    @Override
    public void onChildDrawOver(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                RecyclerView.ViewHolder viewHolder,
                                float dX, float dY, int actionState, boolean isCurrentlyActive) {
        if (viewHolder instanceof ShiftListAdapter.ViewHolder) {
            float clampedX = Math.min(0f, dX);
            getDefaultUIUtil().onDrawOver(c, recyclerView,
                    ((ShiftListAdapter.ViewHolder) viewHolder).foreground,
                    clampedX, dY, actionState, isCurrentlyActive);
        }
    }
}
