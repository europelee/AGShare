package org.eu.weshare.fragment;



import org.eu.weshare.R;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

public class SearchFragment extends Fragment {

  private EditText mContentEdit = null;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    View rootView = null;
    rootView = inflater.inflate(R.layout.fragment_search, container, false);

    mContentEdit = (EditText) rootView.findViewById(R.id.edit_content);

    Button searchBtn = (Button) rootView.findViewById(R.id.content_search);
    searchBtn.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View arg0) {
        // TODO Auto-generated method stub

      }
    });

    return rootView;
  }
}
