/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.os;

import android.os.Handler;
import android.os.Message;

import java.util.ArrayList;
import java.util.HashMap;

/** @hide */
public class RegistrantList
{
    ArrayList   registrants = new ArrayList();      // of Registrant

    public synchronized void
    add(Handler h, int what, Object obj)
    {
        add(new Registrant(h, what, obj));
    }

    public synchronized void
    addUnique(Handler h, int what, Object obj)
    {
        // if the handler is already in the registrant list, remove it
        remove(h);
        add(new Registrant(h, what, obj));
    }

    public synchronized void
    add(Registrant r)
    {
        removeCleared();
        registrants.add(r);
    }

    public synchronized void
    removeCleared()
    {
        for (int i = registrants.size() - 1; i >= 0 ; i--) {
            Registrant  r = (Registrant) registrants.get(i);

            if (r.refH == null) {
                registrants.remove(i);
            }
        }
    }

    public synchronized int
    size()
    {
        return registrants.size();
    }

    public synchronized Object
    get(int index)
    {
        return registrants.get(index);
    }

    private synchronized void
    internalNotifyRegistrants (Object result, Throwable exception)
    {
       for (int i = 0, s = registrants.size(); i < s ; i++) {
            Registrant  r = (Registrant) registrants.get(i);
            r.internalNotifyRegistrant(result, exception);
       }
    }

    public /*synchronized*/ void
    notifyRegistrants()
    {
        internalNotifyRegistrants(null, null);
    }

    public /*synchronized*/ void
    notifyException(Throwable exception)
    {
        internalNotifyRegistrants (null, exception);
    }

    public /*synchronized*/ void
    notifyResult(Object result)
    {
        internalNotifyRegistrants (result, null);
    }


    public /*synchronized*/ void
    notifyRegistrants(AsyncResult ar)
    {
        internalNotifyRegistrants(ar.result, ar.exception);
    }

    public synchronized void
    remove(Handler h)
    {
        for (int i = 0, s = registrants.size() ; i < s ; i++) {
            Registrant  r = (Registrant) registrants.get(i);
            Handler     rh;

            rh = r.getHandler();

            /* Clean up both the requested registrant and
             * any now-collected registrants
             */
            if (rh == null || rh == h) {
                r.clear();
            }
        }

        removeCleared();
    }
}
