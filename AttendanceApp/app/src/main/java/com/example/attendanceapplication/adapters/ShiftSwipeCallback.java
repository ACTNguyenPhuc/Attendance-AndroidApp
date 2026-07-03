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
 * panel "Xóa / Dời ca" nằm phía sau; vuốt qua ngưỡng sẽ giữ panel mở để giáo viên
 * chọn hành động mong muốn. Nút xóa chỉ khả dụng với ca đang ở trạng thái upcoming.
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
        if (!ShiftListAdapter.hasSwipeActions(shift)) return 0;
        return super.getMovementFlags(recyclerView, viewHolder);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target) {
        return false; // không hỗ trợ kéo sắp xếp
    }

    // Vuốt khoảng 25% bề rộng để mở panel hai hành động.
    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        return 0.25f;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        int pos = viewHolder.getBindingAdapterPosition();
        if (pos != RecyclerView.NO_POSITION) adapter.openActions(pos);
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
            ShiftListAdapter.ViewHolder holder = (ShiftListAdapter.ViewHolder) viewHolder;
            float panelWidth = holder.actionPanel.getWidth();
            float clampedX = Math.max(-panelWidth, Math.min(0f, dX));
            getDefaultUIUtil().onDraw(c, recyclerView,
                    holder.foreground,
                    clampedX, dY, actionState, isCurrentlyActive);
        }
    }

    @Override
    public void onChildDrawOver(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                RecyclerView.ViewHolder viewHolder,
                                float dX, float dY, int actionState, boolean isCurrentlyActive) {
        if (viewHolder instanceof ShiftListAdapter.ViewHolder) {
            ShiftListAdapter.ViewHolder holder = (ShiftListAdapter.ViewHolder) viewHolder;
            float panelWidth = holder.actionPanel.getWidth();
            float clampedX = Math.max(-panelWidth, Math.min(0f, dX));
            getDefaultUIUtil().onDrawOver(c, recyclerView,
                    holder.foreground,
                    clampedX, dY, actionState, isCurrentlyActive);
        }
    }
}
