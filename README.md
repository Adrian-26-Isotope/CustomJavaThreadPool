# CustomJavaThreadPool
I was not satisfied with the default java ThreadPoolExecutor, so I implemented my own.

## features
<ul>
 <li>
  <b>minimum threads: </b>
  define the minimum amount of threads that shall always be kept alive. These threads are called 'core' threads.
 </li>
 <li>
  <b>maximum threads: </b>
  define the maximum amount of threads that process tasks simultaneously.
 </li> 
 <li>
  <b>idle timeout: </b>
  define the time after which idle threads are terminated.
 </li>
  <li>
  <b>custom thread factory support: </b>
  use your custom thread factory if required.
 </li>
</ul>

## dependencies
<ul>
<li>java 24+</li>
<li>junit 5</li>
</ul>

## license
this project is licensed under the [MIT License](LICENSE.txt).

## examples
see [CustomThreadPoolTest.java](CustomThreadPoolTest.java) for detailed examples.
