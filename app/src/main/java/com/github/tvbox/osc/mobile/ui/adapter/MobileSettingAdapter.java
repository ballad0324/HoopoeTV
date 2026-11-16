package com.github.tvbox.osc.mobile.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.mobile.ui.LiquidMotion;

import java.util.ArrayList;
import java.util.List;

/**
 * 手机端设置列表：分组标题 + 普通设置行 + 胶囊分段行三种类型。
 * 直接用原生 RecyclerView.Adapter，避免第三方适配器的多类型扩展点在版本间差异。
 */
public class MobileSettingAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_ITEM = 1;
    public static final int TYPE_CAPSULE = 2;
    public static final int TYPE_INPUT = 3;
    public static final int TYPE_SWITCH = 4;

    public interface OnSwitch {
        void onSwitch(Row row, boolean checked);
    }

    public interface OnOption {
        void onOption(Row row, int index);
    }

    public interface OnInput {
        void onInput(Row row, String text);
    }

    private final List<Row> rows = new ArrayList<>();
    /** 首屏设置项错落淡入标记，避免滚动时反复播放 */
    private boolean[] revealed = new boolean[0];

    private OnItemClick onItemClick;

    public interface OnItemClick {
        void onItemClick(Row row);
    }

    public void setOnItemClick(OnItemClick listener) {
        this.onItemClick = listener;
    }

    public void setRows(List<Row> data) {
        rows.clear();
        if (data != null) rows.addAll(data);
        revealed = new boolean[rows.size()];
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.adapter_setting_mobile_header, parent, false);
            return new HeaderHolder(v);
        }
        if (viewType == TYPE_CAPSULE) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.adapter_setting_mobile_capsule, parent, false);
            return new CapsuleHolder(v);
        }
        if (viewType == TYPE_INPUT) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.adapter_setting_mobile_input, parent, false);
            return new InputHolder(v);
        }
        if (viewType == TYPE_SWITCH) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.adapter_setting_mobile_switch, parent, false);
            LiquidMotion.bindPress(v, 0.97f);
            return new SwitchHolder(v);
        }
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.adapter_setting_mobile_item, parent, false);
        // 设置行：液态按压（按下收缩、松手回弹）
        LiquidMotion.bindPress(v, 0.96f);
        return new ItemHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        final Row row = rows.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).header.setText(row.title);
        } else if (holder instanceof ItemHolder) {
            ItemHolder h = (ItemHolder) holder;
            h.title.setText(row.title);
            h.value.setText(row.value == null ? "" : row.value);
            if (position < revealed.length && position < 12 && !revealed[position]) {
                revealed[position] = true;
                LiquidMotion.reveal(h.itemView, position * 28L);
            }
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (onItemClick != null) onItemClick.onItemClick(row);
                }
            });
        } else if (holder instanceof CapsuleHolder) {
            CapsuleHolder h = (CapsuleHolder) holder;
            h.title.setText(row.title == null ? "" : row.title);
            if (row.hint == null || row.hint.length() == 0) {
                h.hint.setVisibility(View.GONE);
            } else {
                h.hint.setVisibility(View.VISIBLE);
                h.hint.setText(row.hint);
            }
            bindCapsules(h, row);
            if (position < revealed.length && position < 12 && !revealed[position]) {
                revealed[position] = true;
                LiquidMotion.reveal(h.itemView, position * 28L);
            }
        } else if (holder instanceof InputHolder) {
            InputHolder h = (InputHolder) holder;
            h.title.setText(row.title == null ? "" : row.title);
            if (row.hint == null || row.hint.length() == 0) {
                h.hint.setVisibility(View.GONE);
            } else {
                h.hint.setVisibility(View.VISIBLE);
                h.hint.setText(row.hint);
            }
            // 输入框预填当前值，用户就地改完点下方按钮即生效
            h.input.setText(row.value == null ? "" : row.value);
            h.input.setHint(row.hintInput == null ? "" : row.hintInput);
            h.action.setText(row.actionText == null ? "立即更改" : row.actionText);
            bindInput(h, row);
            if (position < revealed.length && position < 12 && !revealed[position]) {
                revealed[position] = true;
                LiquidMotion.reveal(h.itemView, position * 28L);
            }
        } else if (holder instanceof SwitchHolder) {
            SwitchHolder h = (SwitchHolder) holder;
            h.title.setText(row.title == null ? "" : row.title);
            if (row.hint == null || row.hint.length() == 0) {
                h.hint.setVisibility(View.GONE);
            } else {
                h.hint.setVisibility(View.VISIBLE);
                h.hint.setText(row.hint);
            }
            bindSwitch(h, row);
            if (position < revealed.length && position < 12 && !revealed[position]) {
                revealed[position] = true;
                LiquidMotion.reveal(h.itemView, position * 28L);
            }
        }
    }

    private void bindInput(InputHolder h, final Row row) {
        h.action.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (row.onInput != null) row.onInput.onInput(row, h.input.getText().toString().trim());
            }
        });
    }

    /** 胶囊开关：整行可点，圆钮在轨道内左右滑动，状态由 Row.checked 持有 */
    private void bindSwitch(final SwitchHolder h, final Row row) {
        final android.content.Context ctx = h.itemView.getContext();
        final float density = ctx.getResources().getDisplayMetrics().density;
        final float onX = density * 20f;
        applySwitch(h, row.checked, onX, false);
        h.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                row.checked = !row.checked;
                applySwitch(h, row.checked, onX, true);
                if (row.onSwitch != null) row.onSwitch.onSwitch(row, row.checked);
            }
        });
    }

    private void applySwitch(SwitchHolder h, boolean checked, float onX, boolean animate) {
        h.track.setBackgroundResource(checked
                ? R.drawable.shape_switch_track_on
                : R.drawable.shape_switch_track_off);
        if (animate) {
            h.thumb.animate().translationX(checked ? onX : 0f).setDuration(160).start();
        } else {
            h.thumb.setTranslationX(checked ? onX : 0f);
        }
    }

    /** 胶囊分段控件：选项等分、当前项高亮为实心胶囊，点一下直接生效（不弹对话框） */
    private void bindCapsules(CapsuleHolder h, final Row row) {
        h.group.removeAllViews();
        if (row.options == null || row.options.length == 0) return;
        final android.content.Context ctx = h.itemView.getContext();
        float density = ctx.getResources().getDisplayMetrics().density;
        int padH = (int) (density * 6 + 0.5f);
        int chipH = (int) (density * 32 + 0.5f);
        int gap = (int) (density * 2 + 0.5f);
        float textSize = row.options.length > 4 ? 11f : 12f;
        for (int i = 0; i < row.options.length; i++) {
            final int index = i;
            TextView chip = new TextView(ctx);
            chip.setText(row.options[i]);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSize);
            chip.setSingleLine(true);
            chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
            chip.setHeight(chipH);
            chip.setPadding(padH, 0, padH, 0);
            if (i == row.selected) {
                chip.setBackgroundResource(R.drawable.shape_capsule_on);
                chip.setTextColor(0xFFFFFFFF);
            } else {
                chip.setBackgroundResource(0);
                chip.setTextColor(com.github.tvbox.osc.util.SkinManager.text2(ctx));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) lp.setMarginStart(gap);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (row.onOption != null) row.onOption.onOption(row, index);
                }
            });
            h.group.addView(chip);
        }
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        TextView header;

        HeaderHolder(View v) {
            super(v);
            header = v.findViewById(R.id.itemHeader);
        }
    }

    static class ItemHolder extends RecyclerView.ViewHolder {
        TextView title;
        TextView value;

        ItemHolder(View v) {
            super(v);
            title = v.findViewById(R.id.itemTitle);
            value = v.findViewById(R.id.itemValue);
        }
    }

    static class CapsuleHolder extends RecyclerView.ViewHolder {
        TextView title;
        TextView hint;
        LinearLayout group;

        CapsuleHolder(View v) {
            super(v);
            title = v.findViewById(R.id.itemTitle);
            hint = v.findViewById(R.id.itemHint);
            group = v.findViewById(R.id.capsuleGroup);
        }
    }

    static class InputHolder extends RecyclerView.ViewHolder {
        TextView title;
        TextView hint;
        EditText input;
        TextView action;

        InputHolder(View v) {
            super(v);
            title = v.findViewById(R.id.itemTitle);
            hint = v.findViewById(R.id.itemHint);
            input = v.findViewById(R.id.itemInput);
            action = v.findViewById(R.id.itemAction);
        }
    }

    static class SwitchHolder extends RecyclerView.ViewHolder {
        TextView title;
        TextView hint;
        View track;
        View thumb;

        SwitchHolder(View v) {
            super(v);
            title = v.findViewById(R.id.itemTitle);
            hint = v.findViewById(R.id.itemHint);
            track = v.findViewById(R.id.switchTrack);
            thumb = v.findViewById(R.id.switchThumb);
        }
    }

    public static class Row {
        public int type;
        public String title;
        public String value;
        public String hint;
        public String hintInput;
        public String actionText;
        public String[] options;
        public int selected;
        public Runnable action;
        public OnOption onOption;
        public OnInput onInput;
        public OnSwitch onSwitch;
        public boolean checked;

        public static Row header(String title) {
            Row row = new Row();
            row.type = TYPE_HEADER;
            row.title = title;
            return row;
        }

        public static Row item(String title, String value, Runnable action) {
            Row row = new Row();
            row.type = TYPE_ITEM;
            row.title = title;
            row.value = value;
            row.action = action;
            return row;
        }

        /** 胶囊分段行：options 为各档文案，selected 为当前档序号 */
        public static Row capsule(String title, String hint, String[] options, int selected, OnOption onOption) {
            Row row = new Row();
            row.type = TYPE_CAPSULE;
            row.title = title;
            row.hint = hint;
            row.options = options;
            row.selected = selected;
            row.onOption = onOption;
            return row;
        }

        /** 输入行：输入框预填 value，点下方按钮回调 onInput */
        public static Row input(String title, String hint, String hintInput, String value,
                                String actionText, OnInput onInput) {
            Row row = new Row();
            row.type = TYPE_INPUT;
            row.title = title;
            row.hint = hint;
            row.hintInput = hintInput;
            row.value = value;
            row.actionText = actionText;
            row.onInput = onInput;
            return row;
        }

        /** 胶囊开关行：checked 为当前状态，点整行切换并回调 onSwitch */
        public static Row switchRow(String title, String hint, boolean checked, OnSwitch onSwitch) {
            Row row = new Row();
            row.type = TYPE_SWITCH;
            row.title = title;
            row.hint = hint;
            row.checked = checked;
            row.onSwitch = onSwitch;
            return row;
        }
    }
}
