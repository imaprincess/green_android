package com.greenaddress.greenbits.ui.accounts;

import android.graphics.drawable.BitmapDrawable;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.greenaddress.greenapi.data.SubaccountData;
import com.greenaddress.greenbits.GaService;
import com.greenaddress.greenbits.ui.components.FontAwesomeTextView;
import com.greenaddress.greenbits.ui.R;
import com.greenaddress.greenbits.ui.UI;

import java.util.ArrayList;

class AccountItemAdapter extends RecyclerView.Adapter<AccountItemAdapter.Item> {

    private final ArrayList<SubaccountData> mSubaccountList;
    private OnAccountSelected mOnAccountSelected;
    private final GaService mService;
    private final boolean mShowCardView;

    interface OnAccountSelected {
        void onAccountSelected(int account);
    }

    public AccountItemAdapter(final ArrayList<SubaccountData> subaccountList, final GaService service,
                              final boolean showCardView) {
        mSubaccountList = subaccountList;
        mService = service;
        mShowCardView = showCardView;
    }

    public void setCallback(final OnAccountSelected cb) {
        mOnAccountSelected = cb;
    }

    @Override
    public Item onCreateViewHolder(final ViewGroup parent, final int viewType) {
        if (mShowCardView) {
            final View view = LayoutInflater.from(parent.getContext())
                              .inflate(R.layout.list_element_account, parent, false);
            return new Item(view);
        }
        final View view = LayoutInflater.from(parent.getContext())
                          .inflate(R.layout.dialog_accountlistitem, parent, false);
        return new Item(view);
    }

    @Override
    public void onBindViewHolder(final Item holder, final int position) {
        Log.d(this.getClass().getName(),"Update position " + String.valueOf(position));
        final int pointer = mSubaccountList.get(position).getPointer();
        holder.mName.setText(mSubaccountList.get(position).getName());

        final ObjectNode balanceData = mService.getBalanceData(pointer).toObjectNode();
        final String valueBitcoin = mService.getValueString(balanceData, false, true);
        final String valueFiat = mService.getValueString(balanceData, true, true);
        holder.mBalance.setText(valueBitcoin);
        holder.mFiatBalance.setText(valueFiat);

        //onDisplayBalance(holder, position);
        final boolean isCurrent = pointer == mSubaccountList.get(position).getPointer();
        holder.mRadio.setChecked(isCurrent);
        final View.OnClickListener listener = view -> mOnAccountSelected.onAccountSelected(position);
        holder.mView.setOnClickListener(listener);
        // Some Android versions do not pass the radio click to the
        // view, so we override its listener here too.
        holder.mRadio.setOnClickListener(listener);
        if (mShowCardView) {

            final String address = mService.getAddress(mSubaccountList.get(position).getPointer());
            if (address == null) {
                Log.d(this.getClass().getName(),"*** ADDRESS "+pointer+" NULL ***");
                return;
            }
            holder.mAddress.setText(address);
            final BitmapDrawable bd = new BitmapDrawable(holder.itemView.getResources(), UI.getQRCode(address));
            bd.setFilterBitmap(false);
            holder.mQrImage.setImageDrawable(bd);
        }
    }

    @Override
    public int getItemCount() {
        return mSubaccountList.size();
    }

    public static class Item extends RecyclerView.ViewHolder {

        final View mView;
        final RadioButton mRadio;
        final TextView mName;
        final FontAwesomeTextView mUnit;
        final TextView mBalance;
        final TextView mFiatBalance;
        final TextView mAddress;
        final ImageView mQrImage;

        public Item(final View v) {
            super(v);
            mView = v;
            mRadio = UI.find(v, R.id.radio);
            mName = UI.find(v, R.id.name);
            mUnit = UI.find(v, R.id.mainBalanceUnit);
            mBalance = UI.find(v, R.id.mainBalanceText);
            mFiatBalance = UI.find(v, R.id.mainLocalBalanceText);
            mAddress = UI.find(v, R.id.receiveAddressText);
            mQrImage = UI.find(v, R.id.receiveQrImageView);
        }

    }
}
