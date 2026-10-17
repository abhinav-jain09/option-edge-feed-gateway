# Fresh dependency packaging

Jenkins gateway744 passed the full1,403-test suite and reproduced all50 recorded
mutation outcomes, but the embedded-artifact check correctly refused packaging.
Maven's incremental package step reused the previous Boot JAR from build743 while
this build had installed a different, independently hash-bound contracts0.10 JAR.
No image was pushed and no deployment ran.

The guarded Package stage now runs `mvn -B -DskipTests clean package`. This removes
the previous archive before compiling and packaging the permitted source against
this build's bound dependency. Tests remain the preceding Jenkins/es4 stage; clean
packaging runs no tests or application. Source/tree guards, embedded contracts hash
verification, qualification receipts and the image gate remain required.

Local validation runs only clean packaging with tests skipped and verifies the
embedded JAR against the exact installed0.10 artifact hash; it passes. Jenkins
Declarative validation and the shared source-guard checker pass. Full correctness
and mutation qualification will run again through Jenkins on es4 before an image
can be published; no receipt from a prior build is reused or rebound.
