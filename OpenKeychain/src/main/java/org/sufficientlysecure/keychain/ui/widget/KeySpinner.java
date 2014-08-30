/*
 * Copyright (C) 2014 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.keychain.ui.widget;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.pgp.KeyRing;
import org.sufficientlysecure.keychain.pgp.PgpKeyHelper;
import org.sufficientlysecure.keychain.provider.KeychainContract;
import org.sufficientlysecure.keychain.util.Log;

public abstract class KeySpinner extends Spinner {
    public interface OnKeyChangedListener {
        public void onKeyChanged(long masterKeyId);
    }

    private long mSelectedKeyId;
    private SelectKeyAdapter mAdapter = new SelectKeyAdapter();
    private OnKeyChangedListener mListener;

    public KeySpinner(Context context) {
        super(context);
        initView();
    }

    public KeySpinner(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public KeySpinner(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView();
    }

    private void initView() {
        setAdapter(mAdapter);
        super.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mListener != null) {
                    mListener.onKeyChanged(id);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if (mListener != null) {
                    mListener.onKeyChanged(Constants.key.none);
                }
            }
        });
    }

    public abstract Loader<Cursor> onCreateLoader();

    @Override
    public void setOnItemSelectedListener(OnItemSelectedListener listener) {
        throw new UnsupportedOperationException();
    }

    public void setOnKeyChangedListener(OnKeyChangedListener listener) {
        mListener = listener;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        reload();
    }

    public void reload() {
        if (getContext() instanceof FragmentActivity) {
            ((FragmentActivity) getContext()).getSupportLoaderManager().restartLoader(hashCode(), null, new LoaderManager.LoaderCallbacks<Cursor>() {
                @Override
                public Loader<Cursor> onCreateLoader(int id, Bundle args) {
                    return KeySpinner.this.onCreateLoader();
                }

                @Override
                public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
                    mAdapter.swapCursor(data);
                }

                @Override
                public void onLoaderReset(Loader<Cursor> loader) {
                    mAdapter.swapCursor(null);
                }
            });
        } else {
            Log.e(Constants.TAG, "KeySpinner must be attached to FragmentActivity, this is " + getContext().getClass());
        }
    }

    public long getSelectedKeyId() {
        return mSelectedKeyId;
    }

    public void setSelectedKeyId(long selectedKeyId) {
        this.mSelectedKeyId = selectedKeyId;
    }

    private class SelectKeyAdapter extends BaseAdapter implements SpinnerAdapter {
        private CursorAdapter inner;
        private int mIndexUserId;
        private int mIndexKeyId;
        private int mIndexMasterKeyId;

        public SelectKeyAdapter() {
            inner = new CursorAdapter(null, null, 0) {
                @Override
                public View newView(Context context, Cursor cursor, ViewGroup parent) {
                    return View.inflate(getContext(), R.layout.keyspinner_key, null);
                }

                @Override
                public void bindView(View view, Context context, Cursor cursor) {
                    String[] userId = KeyRing.splitUserId(cursor.getString(mIndexUserId));
                    ((TextView) view.findViewById(android.R.id.title)).setText(userId[2] == null ? userId[0] : (userId[0] + " (" + userId[2] + ")"));
                    ((TextView) view.findViewById(android.R.id.text1)).setText(userId[1]);
                    ((TextView) view.findViewById(android.R.id.text2)).setText(PgpKeyHelper.convertKeyIdToHex(cursor.getLong(mIndexKeyId)));
                }

                @Override
                public long getItemId(int position) {
                    try {
                        return ((Cursor) getItem(position)).getLong(mIndexMasterKeyId);
                    } catch (Exception e) {
                        // This can happen on concurrent modification :(
                        return Constants.key.none;
                    }
                }
            };
        }

        public Cursor swapCursor(Cursor newCursor) {
            if (newCursor == null) return inner.swapCursor(null);

            mIndexKeyId = newCursor.getColumnIndex(KeychainContract.KeyRings.KEY_ID);
            mIndexUserId = newCursor.getColumnIndex(KeychainContract.KeyRings.USER_ID);
            mIndexMasterKeyId = newCursor.getColumnIndex(KeychainContract.KeyRings.MASTER_KEY_ID);
            if (newCursor.moveToFirst()) {
                do {
                    if (newCursor.getLong(mIndexMasterKeyId) == mSelectedKeyId) {
                        setSelection(newCursor.getPosition() + 1);
                    }
                } while (newCursor.moveToNext());
            }
            return inner.swapCursor(newCursor);
        }

        @Override
        public int getCount() {
            return inner.getCount() + 1;
        }

        @Override
        public Object getItem(int position) {
            if (position == 0) return null;
            return inner.getItem(position - 1);
        }

        @Override
        public long getItemId(int position) {
            if (position == 0) return Constants.key.none;
            return inner.getItemId(position - 1);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            try {
                View v = getDropDownView(position, convertView, parent);
                v.findViewById(android.R.id.text1).setVisibility(View.GONE);
                return v;
            } catch (NullPointerException e) {
                // This is for the preview...
                return View.inflate(getContext(), android.R.layout.simple_list_item_1, null);
            }
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View v;
            if (position == 0) {
                if (convertView == null) {
                    v = inner.newView(null, null, parent);
                } else {
                    v = convertView;
                }
                ((TextView) v.findViewById(android.R.id.title)).setText("None");
                v.findViewById(android.R.id.text1).setVisibility(View.GONE);
                v.findViewById(android.R.id.text2).setVisibility(View.GONE);
            } else {
                v = inner.getView(position - 1, convertView, parent);
                v.findViewById(android.R.id.text1).setVisibility(View.VISIBLE);
                v.findViewById(android.R.id.text2).setVisibility(View.VISIBLE);
            }
            return v;
        }
    }
}