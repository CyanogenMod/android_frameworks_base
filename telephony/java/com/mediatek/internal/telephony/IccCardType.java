/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.mediatek.internal.telephony;

/**
 * Report ICC Card Type.
 */
public class IccCardType {

    /**
      * This card type is report by SVLTE modems.
      * {@hide}
      */
    public enum SvlteCardType {
        INVALID_CARD(0),         //Invalid card type
        UIM_CARD(1),             //With RUIM application
        SIM_CARD(2),             //With only SIM application
        UIM_SIM_CARD(3),         //With RUIM & SIM application, non CT dual mode card
        UNKNOW_CARD(4),          //card is present, but can't detect type
        CT_3G_UIM_CARD(5),       //With RUIM application, CT 3G UIM card
        CT_UIM_SIM_CARD(6),      //With RUIM & SIM application, CT 3G dual mode card
        PIN_LOCK_CARD(7),        //this card need PIN
        CT_4G_UICC_CARD(8),      //With USIM & CSIM application, CT 4G dual mode UICC card
        NOT_CT_UICC_CARD(9),     //With USIM & CSIM application, Non CT, 4G dual mode UICC card
        LOCKED_CARD(18),         //card is locked
        USIM_CARD(100),          //With USIM application only
        CARD_NOT_INSERTED(255);  //card is not inserted

        private int mValue;

        public int getValue() {
            return mValue;
        }

        /**
         * Get CardType from integer.
         * ASSERT: Please DON'T directly use CardType.values(), otherwise JE will occur
         *
         * @param cardTypeInt for cardType index.
         * @return SvlteCardType.
         */
        public static SvlteCardType getCardTypeFromInt(int cardTypeInt) {
            SvlteCardType cardType = INVALID_CARD;
            SvlteCardType[] cardTypes = SvlteCardType.values();
            for (int i = 0; i < cardTypes.length; i++) {
                if (cardTypes[i].getValue() == cardTypeInt) {
                    cardType = cardTypes[i];
                    break;
                }
            }
            return cardType;
        }

        /**
         * Get CardType from String.
         *
         * @param cardType for card applications.
         * @return SvlteCardType.
         */
        public static SvlteCardType transformCardTypeFromString(String cardType) {
            if ("USIM".equals(cardType)) {
                return USIM_CARD;
            } else if ("SIM".equals(cardType)) {
                return SIM_CARD;
            } else {
                return INVALID_CARD;
            }
        }

        /**
         * Check if it is 4G card.
         *
         * @return true if it is 4G card
         */
        public boolean is4GCard() {
            return ((this == CT_4G_UICC_CARD) || (this == NOT_CT_UICC_CARD));
        }

        /**
         * Check if it is 3G card.
         *
         * @return true if it is 3G card
         */
        public boolean is3GCard() {
            return ((this == UIM_CARD) || (this == CT_UIM_SIM_CARD)
                    || (this == CT_3G_UIM_CARD) || (this == CT_UIM_SIM_CARD));
        }

        /**
         * Check if it is dual mode card.
         *
         * @return true if it is dual mode card
         */
        public boolean isDualModeCard() {
            return ((this == UIM_SIM_CARD) || (this == CT_UIM_SIM_CARD)
                    || (this == CT_4G_UICC_CARD) || (this == NOT_CT_UICC_CARD));
        }

        /**
         * Check if it is OP09 card.
         *
         * @return true if it is OP09 card
         */
        public boolean isOPO9Card() {
            return ((this == CT_3G_UIM_CARD) || (this == CT_UIM_SIM_CARD)
            || (this == CT_4G_UICC_CARD));
        }

        /**
         * Check if it is a valid card type.
         *
         * @return true if it is
         */
        public boolean isValidCardType() {
            return this != INVALID_CARD;
        }

        private SvlteCardType(int value) {
            mValue = value;
        }
    }
}
