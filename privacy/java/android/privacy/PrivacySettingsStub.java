/**
 * Copyright (C) 2012 Simeon J Morgan <smorgan@digitalfeed.net>
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, see <http://www.gnu.org/licenses>.
 */

package android.privacy;

/**
 * Acts as a placeholder where PrivacySettings are absent (for caching).
 * DO NOT USE THIS ANYWHERE BUT CACHING! Because it is not final, it can be subclassed, and various other
 * nasty tricks can be used to open security issues
 * @author Simeon J Morgan 
 * {@hide} 
 */
class PrivacySettingsStub {
    private final static boolean isStub = true;
    
    boolean isStub() {
        return isStub;
    }
}
