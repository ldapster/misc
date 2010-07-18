import java.util.Arrays;
import java.util.Random;


/**
 * Test driver to test hasSumValue method.
 *
 */
public class Test {


  /** Tests if two integers in the specified array sum to the specified value.
   *
   * @param a Array of integers.
   * @param v Value to check against.
   * @return <tt>true</tt> if two integers in the array sum the the value
   */
  public static boolean hasSumValue(int[] a, int v) {
    boolean ret = false;
    Arrays.sort(a);
    int h = 0;
    int t = a.length-1;
    while(h != t) {
      int sum = a[h] + a[t];
      if(sum > v)
        t--;
      else if(sum < v)
        h++;
      else if(sum == v) {
        ret = true;
        break;
      }
    }
    return ret;
  }

  private static int[] generateArray(int s) {
    Random r = new Random();
    int[] a = new int[s];
    for(int i =0; i < s; i++)
      a[i]=r.nextInt();
    return a;
  }


  public static void main(String ... args) {
    try {
      if(args.length != 2) {
        System.out.println("Usage: <array_size> <value>");
        System.exit(1);
      }
      int arraySize = Integer.decode(args[0]);
      int searchVal = Integer.decode(args[1]);
      int[] a = generateArray(arraySize);
      System.out.println("Array has two integers that sum to " + searchVal +
                         ": " + hasSumValue(a, searchVal));
    } catch (NumberFormatException nfe) {
      System.out.println(nfe.getMessage());
      nfe.printStackTrace();
    }
  }
}
