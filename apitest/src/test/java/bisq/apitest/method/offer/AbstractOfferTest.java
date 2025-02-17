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

package bisq.apitest.method.offer;

import bisq.core.monetary.Altcoin;

import protobuf.PaymentAccount;

import org.bitcoinj.utils.Fiat;

import java.math.BigDecimal;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import static bisq.apitest.Scaffold.BitcoinCoreApp.bitcoind;
import static bisq.apitest.config.HavenoAppConfig.alicedaemon;
import static bisq.apitest.config.HavenoAppConfig.arbdaemon;
import static bisq.apitest.config.HavenoAppConfig.bobdaemon;
import static bisq.apitest.config.HavenoAppConfig.seednode;
import static bisq.common.util.MathUtils.roundDouble;
import static bisq.common.util.MathUtils.scaleDownByPowerOf10;
import static bisq.core.locale.CurrencyUtil.isCryptoCurrency;
import static java.math.RoundingMode.HALF_UP;



import bisq.apitest.method.MethodTest;

@Slf4j
public abstract class AbstractOfferTest extends MethodTest {

    @Setter
    protected static boolean isLongRunningTest;

    @BeforeAll
    public static void setUp() {
        startSupportingApps(true,
                false,
                bitcoind,
                seednode,
                arbdaemon,
                alicedaemon,
                bobdaemon);
    }

    protected double getScaledOfferPrice(double offerPrice, String currencyCode) {
        int precision = isCryptoCurrency(currencyCode) ? Altcoin.SMALLEST_UNIT_EXPONENT : Fiat.SMALLEST_UNIT_EXPONENT;
        return scaleDownByPowerOf10(offerPrice, precision);
    }

    protected final double getPercentageDifference(double price1, double price2) {
        return BigDecimal.valueOf(roundDouble((1 - (price1 / price2)), 5))
                .setScale(4, HALF_UP)
                .doubleValue();
    }

    @AfterAll
    public static void tearDown() {
        tearDownScaffold();
    }
}
