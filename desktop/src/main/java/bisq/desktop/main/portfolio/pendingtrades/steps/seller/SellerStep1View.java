/*
 * This file is part of Haveno.
 *
 * Haveno is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Haveno is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Haveno. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.portfolio.pendingtrades.steps.seller;

import bisq.desktop.main.portfolio.pendingtrades.PendingTradesViewModel;
import bisq.desktop.main.portfolio.pendingtrades.steps.TradeStepView;

import bisq.core.locale.Res;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SellerStep1View extends TradeStepView {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerStep1View(PendingTradesViewModel model) {
        super(model);
    }

    @Override
    protected void onPendingTradesInitialized() {
        super.onPendingTradesInitialized();
        //validateDepositInputs();
        log.warn("Need to validate fee and/or deposit txs in SellerStep1View for XMR?"); // TODO (woodser): need to validate fee and/or deposit txs in SellerStep1View?
        checkForTimeout();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Info
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getInfoBlockTitle() {
        return Res.get("portfolio.pending.step1.waitForConf");
    }

    @Override
    protected String getInfoText() {
        return Res.get("portfolio.pending.step1.info", Res.get("shared.TheBTCBuyer"));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Warning
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getFirstHalfOverWarnText() {
        return Res.get("portfolio.pending.step1.warn");
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getPeriodOverWarnText() {
        return Res.get("portfolio.pending.step1.openForDispute");
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

//    // Verify that deposit tx inputs are matching the trade fee txs outputs.
//    private void validateDepositInputs() {
//        try {
//            TradeDataValidation.validateDepositInputs(trade);
//        } catch (TradeDataValidation.ValidationException e) {
//            if (!model.dataModel.tradeManager.isAllowFaultyDelayedTxs()) {
//                new Popup().warning(Res.get("portfolio.pending.invalidTx", e.getMessage())).show();
//            }
//        }
//    }
}


