node('linux') { // COMPILE AND JUNIT
   // def src = 'https://github.com/harniman/spring-petclinic.git'
 def src = 'https://github.com/kishorebhatia/spring-petclinic'
    git url: src
    
    sh 'sleep 30'

    ensureMaven()
    //sh 'mvn -o clean package'
     sh 'mvn clean package'
     echo 'build step completed.'
}


/**
 * Deploy Maven on the slave if needed and add it to the path
 */
def ensureMaven() {
    env.PATH = "${tool 'Maven 3.x'}/bin:${env.PATH}"
}
