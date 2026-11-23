package com.exteragram.messenger.ai.ui.activities;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.exteragram.messenger.ai.AiConfig;
import com.exteragram.messenger.ai.data.Message;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.SimpleActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SectionsScrollView;

import java.util.ArrayList;

/**
 * View of the conversation history kept for generation: one block per message with the request
 * on top and the answer below. Long pressing a block copies or removes its turn, and the action
 * bar clears everything.
 */
public class AiHistoryActivity extends BaseFragment {

    private static final int MENU_CLEAR = 1;

    private LinearLayout content;
    private TextView emptyView;
    private SectionsScrollView scrollView;
    private final ArrayList<Message> history = new ArrayList<>();

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(LocaleController.getString(R.string.AIChatSavedConversations));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_CLEAR) {
                    AiConfig.clearConversationHistory();
                    rebuild(context);
                }
            }
        });
        SimpleActionBarMenu menu = simpleActionBar.createMenu();
        menu.addItem(MENU_CLEAR, R.drawable.msg_delete).setContentDescription(LocaleController.getString(R.string.Delete));

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));

        content = new SectionsScrollView.SectionsLinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        scrollView = new SectionsScrollView(context, content, getResourceProvider(), false);
        scrollView.setFillViewport(true);
        content.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        ((FrameLayout) fragmentView).addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        scrollView.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        emptyView = new TextView(context);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setText(LocaleController.getString(R.string.AIChatHistoryEmpty));
        ((FrameLayout) fragmentView).addView(emptyView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        rebuild(context);
        return fragmentView;
    }

    /** The scrollable history the glass header samples. */
    @Override
    protected SectionsScrollView getScrollViewForSimpleGlass() {
        return scrollView;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    private void rebuild(Context context) {
        if (content == null) {
            return;
        }
        content.removeAllViews();
        history.clear();
        ArrayList<Message> stored = AiConfig.getConversationHistory();
        if (stored != null) {
            history.addAll(stored);
        }
        boolean empty = history.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            return;
        }
        for (int i = 0; i < history.size(); i++) {
            Message message = history.get(i);
            boolean outgoing = message != null && "user".equals(message.role());
            content.addView(createBlock(context, i, outgoing, message == null ? "" : message.content()));
        }
    }

    /**
     * A turn is a request plus its answer, so removing either half removes both. The list is
     * rewritten from scratch afterwards, which keeps the stored order identical to the screen.
     */
    private void deleteTurn(int index) {
        if (index < 0 || index >= history.size()) {
            return;
        }
        int from = index;
        int to = index;
        if ("assistant".equals(history.get(index).role())) {
            if (index > 0 && "user".equals(history.get(index - 1).role())) {
                from = index - 1;
            }
        } else if (index + 1 < history.size() && "assistant".equals(history.get(index + 1).role())) {
            to = index + 1;
        }
        for (int i = to; i >= from; i--) {
            history.remove(i);
        }
        ArrayList<Message> remaining = new ArrayList<>(history);
        AiConfig.saveConversationHistory(remaining);
        Context context = getParentActivity() != null ? getParentActivity() : fragmentView.getContext();
        rebuild(context);
    }

    private View createBlock(Context context, int index, boolean outgoing, String text) {
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12),
                Theme.getColor(outgoing ? Theme.key_chat_outBubble : Theme.key_windowBackgroundWhite, resourceProvider)));
        int padding = AndroidUtilities.dp(12);
        block.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        title.setText(outgoing ? LocaleController.getString(R.string.AIChatPromptHint) : LocaleController.getString(R.string.AIChat));
        block.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView body = new TextView(context);
        body.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        body.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        body.setTextIsSelectable(true);
        body.setText(TextUtils.isEmpty(text) ? " " : text);
        block.addView(body, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(4), AndroidUtilities.dp(12), AndroidUtilities.dp(4));
        // the block draws its own rounded bubble: no section card behind it
        wrapper.setTag(RecyclerListView.TAG_NOT_SECTION);
        wrapper.addView(block, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        wrapper.setOnLongClickListener(v -> {
            showBlockOptions(v, index, text);
            return true;
        });
        return wrapper;
    }

    private void showBlockOptions(View anchor, int index, String text) {
        ItemOptions options = ItemOptions.makeOptions((ViewGroup) fragmentView, resourceProvider, anchor)
                .setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
        options.add(R.drawable.msg_copy, LocaleController.getString(R.string.Copy), () -> {
            if (AndroidUtilities.addToClipboard(text == null ? "" : text)) {
                BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
            }
        });
        options.add(R.drawable.msg_delete, LocaleController.getString(R.string.Delete), true, () -> deleteTurn(index));
        options.show();
    }
}
