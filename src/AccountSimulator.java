import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;


/**
 * Muti-threaded simulator to transfer amounts from a source account to
 * destination account until a single account reaches zero balance.
 *
 */
public class AccountSimulator {

  /* Maximum number of accounts */
  private final static int MAX_ACCOUNTS = 10000;

  /* Maximum number of threads */
  private final static int MAX_THREADS = 200;

  /* Account ids start at this number */
  private final static int ID_BASE = 1000;

  /* Make larger to increase the accounts initial balance sizes. */
  private final static int FACTOR = 100000;

  /* Zero balance */
  private final static BigDecimal ZERO = new BigDecimal(0.0);

  /* Map of the account IDs to accounts */
  private final Map<Integer, Account> accounts =
          new HashMap<Integer, Account>();

  /* Counter of total transactions performed */
  private final AtomicLong totTransactions = new AtomicLong(0);

  /* First empty balance account found */
  private volatile Account emptyAccount;

  /* Specified thread count and number of accounts */
  private final int threadCount, numberOfAccounts;

  /* Specified transfer amount */
  private final BigDecimal transferAmt;

  /** Sole constructor. Cannot be instantiated. Initializes the accounts.
   *
   * @param threadCount The number of threads to create.
   * @param transferAmt The amount to transfer between accounts.
   * @param numberOfAccounts The number of accounts to create.
   */
  private AccountSimulator(int threadCount, BigDecimal transferAmt,
                           int numberOfAccounts) {
    this.threadCount = threadCount;
    this.numberOfAccounts = numberOfAccounts;
    this.transferAmt = transferAmt;
    initAccounts();
  }

  /** Creates the specified number of tasks and executes the simulation. When
   *  the simulation completes, it prints run statistics.
   *
   * @throws InterruptedException if a task was interrupted while waiting.
   * @throws ExecutionException if a computation threw an exception.
   */
  public void execute() throws InterruptedException, ExecutionException {
    long startTime = System.currentTimeMillis();
    ExecutorService transferService = Executors.newFixedThreadPool(threadCount);
    List<Callable<Void>> transferTasks = new LinkedList<Callable<Void>>();
    for (int i = 0; i < threadCount; i++)
      transferTasks.add(new TransferTask());
    List<Future<Void>> results = transferService.invokeAll(transferTasks);
    for (Future<Void> result : results) {
      if (!result.isDone()) {
        result.get();
      }
    }
    transferService.shutdown();
    long totTime = (System.currentTimeMillis() - startTime);
    System.out.println("Zero balance account ID: " + emptyAccount.getID()
        + ", initial bal: " + emptyAccount.getStartBalance()
        + ", credits: " + emptyAccount.getCredits()
        + ", debits: " + emptyAccount.getDebits()
        + ", total: " + emptyAccount.getTransactions());
    System.out.println("Time: " + (totTime / 1000)
        + "s, transactions: " + totTransactions.get());
    System.out.println("threads: " + threadCount + ", amt: " + transferAmt
        + ", accounts: " + numberOfAccounts);
  }

  private void initAccounts() {
    Random r = new Random();
    for (int i = 0; i < numberOfAccounts; i++) {
      BigDecimal v = new BigDecimal(1 + r.nextInt(FACTOR));
      Account a = new Account(ID_BASE + i, transferAmt.multiply(v));
      accounts.put(new Integer(ID_BASE + i), a);
    }
  }

  /**
   * Create a simulator instance using the specified thread count, transfer
   * amount and number of accounts.
   *
   * @param tc The number of threads to create.
   * @param a The transfer amount.
   * @param accounts The number of accounts to create.
   * @return A AccountSimulator instance.
   */
  public static
  AccountSimulator getInstance(int tc, BigDecimal a, int accounts) {
    return new AccountSimulator(tc, a, accounts);
  }

  public static void main(String... args) {
    try {
      if(args.length != 3) {
        System.out.println("Usage: <thead_count> <trans_amt> <number_accts>");
        System.exit(1);
      }
      int tc = Integer.decode(args[0]);
      if (tc <= 0 || tc > MAX_THREADS) {
        System.out.println("Thread count must be between 1 and "
            + MAX_THREADS);
        System.exit(tc);
      }
      BigDecimal amt =
        new BigDecimal(args[1]).setScale(2, RoundingMode.HALF_UP);
      if (amt.compareTo(ZERO) <= 0) {
        System.out.println("Transfer amt must be greater 0.");
        System.exit(1);
      }
      int numAccounts = Integer.decode(args[2]);
      if (numAccounts < 2 || numAccounts > MAX_ACCOUNTS) {
        System.out.println("Number of accounts must be between 2 and "
            + MAX_ACCOUNTS);
        System.exit(numAccounts);
      }
      AccountSimulator simulator = AccountSimulator.getInstance(tc, amt,
          numAccounts);
      simulator.execute();
    } catch (NumberFormatException nfe) {
      System.out.println(nfe.getMessage());
      nfe.printStackTrace();
    } catch (InterruptedException ie) {
      System.out.println(ie.getMessage());
      ie.printStackTrace();
    } catch (ExecutionException e) {
      System.out.println(e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * The task that each thread executes to transfer amounts.
   *
   */
  private final class TransferTask implements Callable<Void> {
    private final Random r = new Random();

    @Override
    public Void call() throws Exception {
      while (emptyAccount == null) {
        Account src = getAccount();
        Account dest = getAccount();
        if (src == dest)
          continue;
        if (src.lock()) {
          try {
            if (dest.lock()) {
              try {
                if (src.isEmptyBalance() && emptyAccount == null) {
                  emptyAccount = src;
                } else if (emptyAccount == null) {
                  src.debit(transferAmt);
                  dest.credit(transferAmt);
                }
              } finally {
                dest.unlock();
              }
            }
          } finally {
            src.unlock();
          }
        }
      }
      return null;
    }

    private Account getAccount() {
      return accounts.get(new Integer(ID_BASE + r.nextInt(numberOfAccounts)));
    }
  }

  /**
   * Represents an account to credit or debit the transfer amount to/from.
   *
   */
  private final class Account {
    private final int accountID;
    private BigDecimal balance;
    private final BigDecimal startBalance;
    private final AtomicLong debits = new AtomicLong(0);
    private final AtomicLong credits = new AtomicLong(0);
    private final ReentrantLock lock = new ReentrantLock();

    Account(int accountID, BigDecimal initial) {
      this.accountID = accountID;
      balance = initial;
      startBalance = initial;
    }

    void unlock() {
      lock.unlock();
    }

    boolean isEmptyBalance() {
      return balance.compareTo(ZERO) == 0 ? true : false;
    }

    boolean lock() {
      return lock.tryLock();
    }

    int getID() {
      return accountID;
    }

    void debit(BigDecimal amt) {
      balance = balance.subtract(amt);
      totTransactions.getAndIncrement();
      debits.getAndIncrement();
    }

    void credit(BigDecimal amt) {
      balance = balance.add(amt);
      totTransactions.getAndIncrement();
      credits.getAndIncrement();
    }

    long getTransactions() {
      return credits.get() + debits.get();
    }

    long getDebits() {
      return debits.get();
    }

    long getCredits() {
      return credits.get();
    }

    BigDecimal getStartBalance() {
      return startBalance;
    }

    @Override
    public int hashCode() {
      return accountID;
    }

    @Override
    public boolean equals(Object o) {
      if (o == this)
        return true;
      if (!(o instanceof Account))
        return false;
      return accountID == ((Account) o).getID();
    }
  }
}
