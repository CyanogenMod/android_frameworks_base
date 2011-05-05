/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.view;

import com.android.layoutlib.bridge.android.BridgeInflater;
import com.android.tools.layoutlib.annotations.LayoutlibDelegate;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import android.util.Xml;

import java.io.IOException;

/**
 * Delegate used to provide new implementation of a select few methods of {@link LayoutInflater}
 *
 * Through the layoutlib_create tool, the original  methods of LayoutInflater have been replaced
 * by calls to methods of the same name in this delegate class.
 *
 * Generally we don't want to copy-paste a huge method like that just for one small features,
 * but because this is done after this platform is final and the next version (Honeycomb) has a
 * better mechanism (or slightly less copy-paste), maintenance of this duplicated code is not
 * a problem.
 *
 */
public class LayoutInflater_Delegate {

    @LayoutlibDelegate
    /*package*/ static void parseInclude(LayoutInflater thisInflater,
            XmlPullParser parser, View parent, AttributeSet attrs)
            throws XmlPullParserException, IOException {

        int type;

        if (parent instanceof ViewGroup) {
            final int layout = attrs.getAttributeResourceValue(null, "layout", 0);
            if (layout == 0) {
                final String value = attrs.getAttributeValue(null, "layout");
                if (value == null) {
                    throw new InflateException("You must specifiy a layout in the"
                            + " include tag: <include layout=\"@layout/layoutID\" />");
                } else {
                    throw new InflateException("You must specifiy a valid layout "
                            + "reference. The layout ID " + value + " is not valid.");
                }
            } else {
                final XmlResourceParser childParser =
                    thisInflater.getContext().getResources().getLayout(layout);

                try {
                    final AttributeSet childAttrs = Xml.asAttributeSet(childParser);

                    while ((type = childParser.next()) != XmlPullParser.START_TAG &&
                            type != XmlPullParser.END_DOCUMENT) {
                        // Empty.
                    }

                    if (type != XmlPullParser.START_TAG) {
                        throw new InflateException(childParser.getPositionDescription() +
                                ": No start tag found!");
                    }

                    final String childName = childParser.getName();

                    if (LayoutInflater.TAG_MERGE.equals(childName)) {
                        // ---- START MODIFICATIONS ----
                        if (thisInflater instanceof BridgeInflater) {
                            ((BridgeInflater) thisInflater).setIsInMerge(true);
                        }
                        // ---- END MODIFICATIONS ----

                        // Inflate all children.
                        thisInflater.rInflate(childParser, parent, childAttrs);

                        // ---- START MODIFICATIONS ----
                        if (thisInflater instanceof BridgeInflater) {
                            ((BridgeInflater) thisInflater).setIsInMerge(false);
                        }
                        // ---- END MODIFICATIONS ----
                    } else {
                        final View view = thisInflater.createViewFromTag(childName, childAttrs);
                        final ViewGroup group = (ViewGroup) parent;

                        // We try to load the layout params set in the <include /> tag. If
                        // they don't exist, we will rely on the layout params set in the
                        // included XML file.
                        // During a layoutparams generation, a runtime exception is thrown
                        // if either layout_width or layout_height is missing. We catch
                        // this exception and set localParams accordingly: true means we
                        // successfully loaded layout params from the <include /> tag,
                        // false means we need to rely on the included layout params.
                        ViewGroup.LayoutParams params = null;
                        try {
                            params = group.generateLayoutParams(attrs);
                        } catch (RuntimeException e) {
                            params = group.generateLayoutParams(childAttrs);
                        } finally {
                            if (params != null) {
                                view.setLayoutParams(params);
                            }
                        }

                        // Inflate all children.
                        thisInflater.rInflate(childParser, view, childAttrs);

                        // Attempt to override the included layout's android:id with the
                        // one set on the <include /> tag itself.
                        TypedArray a = thisInflater.mContext.obtainStyledAttributes(attrs,
                            com.android.internal.R.styleable.View, 0, 0);
                        int id = a.getResourceId(com.android.internal.R.styleable.View_id, View.NO_ID);
                        // While we're at it, let's try to override android:visibility.
                        int visibility = a.getInt(com.android.internal.R.styleable.View_visibility, -1);
                        a.recycle();

                        if (id != View.NO_ID) {
                            view.setId(id);
                        }

                        switch (visibility) {
                            case 0:
                                view.setVisibility(View.VISIBLE);
                                break;
                            case 1:
                                view.setVisibility(View.INVISIBLE);
                                break;
                            case 2:
                                view.setVisibility(View.GONE);
                                break;
                        }

                        group.addView(view);
                    }
                } finally {
                    childParser.close();
                }
            }
        } else {
            throw new InflateException("<include /> can only be used inside of a ViewGroup");
        }

        final int currentDepth = parser.getDepth();
        while (((type = parser.next()) != XmlPullParser.END_TAG ||
                parser.getDepth() > currentDepth) && type != XmlPullParser.END_DOCUMENT) {
            // Empty
        }
    }
}
