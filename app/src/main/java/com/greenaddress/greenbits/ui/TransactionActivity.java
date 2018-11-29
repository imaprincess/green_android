package com.greenaddress.greenbits.ui;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.greenaddress.gdk.GDKSession;
import com.greenaddress.greenapi.JSONMap;
import com.greenaddress.greenapi.data.BumpTxData;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import static com.greenaddress.greenbits.ui.ScanActivity.INTENT_STRING_TX;


public class TransactionActivity extends LoggedActivity implements View.OnClickListener {

    private static final String TAG = TransactionActivity.class.getSimpleName();
    private static final int FEE_BLOCK_NUMBERS[] = {1, 3, 6};

    private Menu mMenu;
    private TextView mEstimatedBlocks;
    private Button mBumpFeeButton;
    private View mMemoTitle;
    private TextView mMemoIcon;
    private TextView mMemoText;
    private TextView mMemoEditText;
    private TextView mUnconfirmedText;
    private Button mMemoSaveButton;
    private Button mExplorerButton;
    private Dialog mSummary;
    private Dialog mTwoFactor;

    private TransactionItem mTxItem;

    @Override
    protected int getMainViewId() { return R.layout.activity_transaction; }

    @Override
    protected void onCreateWithService(final Bundle savedInstanceState) {
        setResult(RESULT_OK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        setTitleBackTransparent();

        mMemoTitle = UI.find(this, R.id.txMemoTitle);
        mMemoIcon = UI.find(this, R.id.sendToNoteIcon);
        mMemoText = UI.find(this, R.id.txMemoText);
        mMemoEditText = UI.find(this, R.id.sendToNoteText);
        mMemoSaveButton = UI.find(this, R.id.saveMemo);
        mEstimatedBlocks = UI.find(this, R.id.txUnconfirmedEstimatedBlocks);
        mBumpFeeButton = UI.find(this, R.id.txUnconfirmedIncreaseFee);
        mExplorerButton = UI.find(this, R.id.txExplorer);
        mUnconfirmedText = UI.find(this, R.id.txUnconfirmedText);

        final TextView doubleSpentByText = UI.find(this, R.id.txDoubleSpentByText);
        final TextView doubleSpentByTitle = UI.find(this, R.id.txDoubleSpentByTitle);

        mTxItem = (TransactionItem) getIntent().getSerializableExtra("TRANSACTION");
        final boolean isWatchOnly = mService.isWatchOnly();

        // Set txid
        final TextView hashText = UI.find(this, R.id.txHashText);
        hashText.setText(mTxItem.txHash.toString());

        // Set explorer button
        final String blockExplorerTx = mService.getNetwork().getTxExplorerUrl();
        openInBrowser(mExplorerButton, mTxItem.txHash.toString(), blockExplorerTx, null);

        // Set title: incoming, outgoing, redeposited
        final String title;
        if (mService.isElements())
            title = "";
        else if (mTxItem.type == TransactionItem.TYPE.OUT)
            title = getString(R.string.id_outgoing);
        else if (mTxItem.type == TransactionItem.TYPE.REDEPOSIT)
            title = getString(R.string.id_redeposited);
        else
            title = getString(R.string.id_incoming);
        setTitle(title);

        // Set state: unconfirmed, completed, pending
        final boolean verified = mTxItem.spvVerified || mTxItem.isSpent ||
                                 mTxItem.type == TransactionItem.TYPE.OUT ||
                                 !mService.isSPVEnabled();
        final String confirmations;
        final int confirmationsColor;
        if (!verified) {
            confirmations = getString(R.string.id_unconfirmed);
            confirmationsColor = R.color.red;
        } else if (mTxItem.getConfirmations() == 0) {
            confirmations = getString(R.string.id_unconfirmed);
            confirmationsColor = R.color.red;
        } else if (!mTxItem.hasEnoughConfirmations()) {
            confirmations = String.format(Locale.US, "%d/6", mTxItem.getConfirmations());
            confirmationsColor = R.color.grey_light;
        } else {
            confirmations = getString(R.string.id_completed);
            confirmationsColor = R.color.grey_light;
        }
        mUnconfirmedText.setText(confirmations);
        mUnconfirmedText.setTextColor(getResources().getColor(confirmationsColor));

        // Set amount
        boolean negative = mTxItem.amount < 0;
        final ObjectNode amount = mService.getSession().convertSatoshi(negative ? -mTxItem.amount : mTxItem.amount);
        final String btc = mService.getValueString(amount, false, true);
        final String fiat = mService.getValueString(amount, true, true);
        final String neg = negative ? "-" : "";
        final TextView amountText = UI.find(this, R.id.txAmountText);
        amountText.setText(String.format("%s%s / %s%s", neg, btc, neg, fiat));

        // Set date/time
        final TextView dateText = UI.find(this, R.id.txDateText);
        dateText.setText(SimpleDateFormat.getInstance().format(mTxItem.date));

        // Set fees
        showFeeInfo(mTxItem.fee, mTxItem.vSize, mTxItem.feeRate);
        UI.hide(mEstimatedBlocks, mBumpFeeButton);
        if (mTxItem.type == TransactionItem.TYPE.OUT || mTxItem.type == TransactionItem.TYPE.REDEPOSIT ||
            mTxItem.isSpent) {
            if (mTxItem.getConfirmations() == 0)
                showUnconfirmed();
        }
        // FIXME: use a list instead of reusing a TextView to show all double spends to allow
        // for a warning to be shown before the browser is open
        // this is to prevent to accidentally leak to block explorers your addresses
        if (mTxItem.doubleSpentBy != null || !mTxItem.replacedHashes.isEmpty()) {
            CharSequence res = "";
            if (mTxItem.doubleSpentBy != null) {
                if (mTxItem.doubleSpentBy.equals("malleability") || mTxItem.doubleSpentBy.equals("update"))
                    res = mTxItem.doubleSpentBy;
                else
                    res = Html.fromHtml(
                        "<a href=\"" + blockExplorerTx + mTxItem.doubleSpentBy + "\">" + mTxItem.doubleSpentBy +
                        "</a>");
                if (!mTxItem.replacedHashes.isEmpty())
                    res = TextUtils.concat(res, "; ");
            }
            if (!mTxItem.replacedHashes.isEmpty()) {
                res = TextUtils.concat(res, Html.fromHtml("replaces transactions:<br/>"));
                for (int i = 0; i < mTxItem.replacedHashes.size(); ++i) {
                    if (i > 0)
                        res = TextUtils.concat(res, Html.fromHtml("<br/>"));
                    final String txHashHex = mTxItem.replacedHashes.get(i).toString();
                    final String link = "<a href=\"" + blockExplorerTx + txHashHex + "\">" + txHashHex + "</a>";
                    res = TextUtils.concat(res, Html.fromHtml(link));
                }
            }
            doubleSpentByText.setText(res);
        }
        UI.showIf(
            mTxItem.doubleSpentBy != null || !mTxItem.replacedHashes.isEmpty(), doubleSpentByText, doubleSpentByTitle);

        // Set recipient / received on
        final TextView receivedOnText = UI.find(this, R.id.txReceivedOnText);
        final TextView receivedOnTitle = UI.find(this, R.id.txReceivedOnTitle);
        final TextView recipientText = UI.find(this, R.id.txRecipientText);
        final TextView recipientTitle = UI.find(this, R.id.txRecipientTitle);
        if (!TextUtils.isEmpty(mTxItem.counterparty)) {
            recipientText.setText(mTxItem.counterparty);
            UI.hide(receivedOnText, receivedOnTitle);
        }

        final int subaccount = mService.getSession().getCurrentSubaccount();
        receivedOnText.setText(mService.getModel().getSubaccountDataObservable().getSubaccountDataWithPointer(
                                   subaccount).getName());

        UI.hideIf(mTxItem.type == TransactionItem.TYPE.REDEPOSIT, UI.find(this, R.id.txRecipientReceiverView));
        UI.hideIf(mTxItem.type == TransactionItem.TYPE.IN, recipientText, recipientTitle);

        // Memo
        if (!TextUtils.isEmpty(mTxItem.memo)) {
            mMemoText.setText(mTxItem.memo);
            UI.hideIf(isWatchOnly, mMemoIcon);
        } else {
            UI.hideIf(isWatchOnly, mMemoTitle, mMemoIcon);
        }

        if (!isWatchOnly) {
            mMemoIcon.setOnClickListener(this);
            mMemoSaveButton.setOnClickListener(this);
        }
    }

    private void showFeeInfo(final long fee, final long vSize, final long feeRate) {

        final TextView feeText = UI.find(this, R.id.txFeeInfoText);
        final String btcFee = mService.getValueString(mService.getSession().convertSatoshi(fee), false, true);
        feeText.setText(String.format("%s, %s vbytes, %s sat/vbyte", btcFee,
                                      String.valueOf(vSize), getFeeRateString(feeRate)));
    }

    private String getFeeRateString(final long feePerKB) {
        final double feePerByte = feePerKB / 1000.0;
        return (new DecimalFormat(".##")).format(feePerByte);
    }

    private void showUnconfirmed() {
        final List<Long> estimates = mService.getFeeEstimates();
        int block = 1;
        while (block < estimates.size()) {
            if (mTxItem.feeRate >= estimates.get(block)) {
                break;
            }
            ++block;
        }

        UI.show(mEstimatedBlocks);
        mEstimatedBlocks.setText(getString(R.string.id_will_confirm_after_blocks, block));

        if (mService.isWatchOnly() || mService.isElements() || !mTxItem.replaceable)
            return; // FIXME: Implement RBF for elements

        // Allow RBF if it might decrease the number of blocks until confirmation
        final boolean allowRbf = block > 1 || mService.getNetwork().alwaysAllowRBF();

        UI.show(mBumpFeeButton);
        mBumpFeeButton.setOnClickListener(this);
    }

    @Override
    public void onResumeWithService() {
        if (!mService.getConnectionManager().isLoggingInOrMore()) {
            // FIXME: Should pass flag to activity so it shows it was forced logged out
            startActivity(new Intent(this, FirstScreenActivity.class));
            finish();
            return;
        }

        setMenuItemVisible(mMenu, R.id.action_share, !mService.getConnectionManager().isPostLogin());
    }

    @Override
    public void onPauseWithService() {
        mSummary = UI.dismiss(this, mSummary);
        mTwoFactor = UI.dismiss(this, mTwoFactor);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        UI.unmapClick(mMemoIcon);
        UI.unmapClick(mMemoSaveButton);
        UI.unmapClick(mBumpFeeButton);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_transaction, menu);
        mMenu = menu;
        return true;
    }

    @Override
    public void onClick(final View v) {
        if (v == mMemoIcon)
            onMemoIconClicked();
        else if (v == mMemoSaveButton)
            onMemoSaveButtonClicked();
        else if (v == mBumpFeeButton)
            onBumpFeeButtonClicked();
    }

    private void onMemoIconClicked() {
        final boolean editInProgress = mMemoEditText.getVisibility() == View.VISIBLE;
        mMemoEditText.setText(UI.getText(mMemoText));
        UI.hideIf(editInProgress, mMemoEditText, mMemoSaveButton);
        UI.showIf(editInProgress, mMemoText);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
        case R.id.action_share:
            final Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT,
                                mService.getNetwork().getTxExplorerUrl() + mTxItem.txHash.toString());
            sendIntent.setType("text/plain");
            startActivity(sendIntent);
            return true;
        case android.R.id.home:
            finish();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    private void onFinishedSavingMemo() {
        runOnUiThread(() -> {
            mMemoText.setText(UI.getText(mMemoEditText));
            UI.hide(mMemoEditText, mMemoSaveButton);
            UI.hideIf(UI.getText(mMemoText).isEmpty(),
                      mMemoText);
            hideKeyboardFrom(mMemoEditText);
        });
        // Force reload tx
        final int subaccount = mService.getSession().getCurrentSubaccount();
        mService.getModel().getTransactionDataObservable(subaccount).refresh();
    }

    private void onMemoSaveButtonClicked() {
        final String newMemo = UI.getText(mMemoEditText);
        if (newMemo.equals(UI.getText(mMemoText))) {
            onFinishedSavingMemo();
            return;
        }

        CB.after(mService.changeMemo(mTxItem.txHash.toString(), newMemo),
                 new CB.Toast<Boolean>(this) {
            @Override
            public void onSuccess(final Boolean result) {
                onFinishedSavingMemo();
            }
        });
    }

    private void openInBrowser(final Button button, final String identifier, final String url,
                               final JSONMap confidentialData) {
        button.setOnClickListener(v -> {
            if (TextUtils.isEmpty(url))
                return;

            String domain = url;
            try {
                domain = new URI(url).getHost();
            } catch (final URISyntaxException e) {
                e.printStackTrace();
            }

            if (TextUtils.isEmpty(domain))
                return;

            final String stripped = domain.startsWith("www.") ? domain.substring(4) : domain;

            UI.popup(TransactionActivity.this, R.string.id_view_in_explorer, R.string.id_continue, R.string.id_cancel)
            .content(getString(R.string.id_are_you_sure_you_want_to_view, stripped))
            .onPositive(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(final MaterialDialog dlg, final DialogAction which) {
                    final String fullUrl = TextUtils.concat(url, identifier).toString();
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)));
                }
            }).build().show();
        });
    }

    private void onBumpFeeButtonClicked() {
        Log.d(TAG,"onBumpFeeButtonClicked");
        try {
            startLoading();
            final GDKSession session = mService.getSession();
            final String txhash = mTxItem.txHash.toString();
            final int subaccount = session.getCurrentSubaccount();
            final JsonNode txToBump = session.getTransactionRaw(subaccount, txhash);
            final JsonNode feeRate = txToBump.get("fee_rate");
            BumpTxData bumpTxData = new BumpTxData();
            bumpTxData.setPreviousTransaction(txToBump);
            bumpTxData.setFeeRate(feeRate.asLong());
            Log.d(TAG,"createTransactionRaw(" + bumpTxData.toString() + ")");
            final ObjectNode tx = session.createTransactionRaw(bumpTxData);
            final Intent intent = new Intent(this, SendActivity.class);
            intent.putExtra(INTENT_STRING_TX, tx.toString());
            startActivity(intent);
            finish();
        } catch (Exception e) {
            UI.toast(this,e.getMessage(), Toast.LENGTH_LONG);
            Log.e(TAG,e.getMessage());
            e.printStackTrace();
        }
    }

}
