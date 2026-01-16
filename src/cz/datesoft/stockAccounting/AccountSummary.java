package cz.datesoft.stockAccounting;

/**
 * Account summary information returned by Trading 212 API test connection
 */
public class AccountSummary {
    // Basic account info
    public String accountId;
    public String currency;
    public double totalValue;

    // Cash breakdown
    public double availableToTrade;
    public double reservedForOrders;
    public double cashInPies;
    public double totalCash;

    // Investment details
    public double investmentsCurrentValue;
    public double investmentsTotalCost;
    public double realizedProfitLoss;
    public double unrealizedProfitLoss;

    // Legacy fields for backward compatibility
    public String accountType;
    public double cashBalance;
    public String accountStatus;

    public AccountSummary() {}

    @Override
    public String toString() {
        return String.format("AccountSummary{id='%s', currency='%s', totalValue=%.2f, totalCash=%.2f, investmentsValue=%.2f}",
                accountId, currency, totalValue, totalCash, investmentsCurrentValue);
    }
}