def sharedHome='/Users/Shared'
/// Set up the list of servers we can deploy to
catalinaHome='/Library/Tomcat' //We need to set this up as a binding class for methods to see
def qaCatalinaBase = "${sharedHome}/apache-tomcat-8-qa"
def qaHttpPort = 8091 // shutdown port = 8005

def stagingCatalinaBase = "${sharedHome}/apache-tomcat-8-staging"
def stagingHttpPort = 8092 // shutdown port = 8006

def perfsCatalinaBase = "${sharedHome}/apache-tomcat-8-perf"
def perfsHttpPort = 8094 // shutdown port = 8008

def productionCatalinaBase = "${sharedHome}/apache-tomcat-8-prod"
def productionHttpPort = 8093 // shutdown port = 8007
///---------- Start Workflow Stages---------
stage 'Build'
node('linux') {
    // COMPILE AND JUNIT
    def src = 'https://github.com/harniman/spring-petclinic.git'
    // def src = '/Users/nharniman/git/harniman/spring-petclinic'
    git url: src

    ensureMaven()
    sh 'mvn -o clean package'
    sh 'tar -c -f src.tar src/ pom.xml'
    archive 'src.tar, target/petclinic.war'
    step $class: 'hudson.tasks.junit.JUnitResultArchiver', testResults: 'target/surefire-reports/*.xml'
    echo "INFO - Ending build phase"
}

stage name: 'Quality analysis and Perfs', concurrency: 1
parallel(qualityAnalysis: {

    node('linux') {
        // RUN SONAR ANALYSIS
        echo "INFO - Starting SONAR"
        unarchive mapping: ['src.tar': '.']
        ensureMaven()
        sh 'tar -x -f src.tar'
        sh 'mvn -o sonar:sonar'
        echo "INFO - Ending SONAR"
    }
}, performanceTest: {

    node('linux') {
        // DEPLOY ON PERFS AND RUN JMETER STRESS TEST
        echo "INFO - starting Perf Tests"
        sh 'rm -rf *'
        unarchive mapping: ['src.tar': '.', 'target/petclinic.war': 'petclinic.war']

        deployApp 'petclinic.war', perfsCatalinaBase, perfsHttpPort

        ensureMaven()
        sh 'tar -x -f src.tar'
       // sh 'mvn -o jmeter:jmeter'

        shutdownApp(perfsCatalinaBase)
        echo "INFO - Ending Perf Tests"
    }
}
)

stage name: 'QA', concurrency: 1
checkpoint 'ENTER QA'

stage name: 'Staging', concurrency: 1
checkpoint 'CHOOSE TO ENTER STAGING'
node('linux') {
    //CLEAN UP RUNNING SERVERS
    def hosts=[
        qaCatalinaBase,
        stagingCatalinaBase,
        perfsCatalinaBase,
        productionCatalinaBase
    ].toArray()
    def host
    for (int i =0; i < hosts.length; i++) {
        host=hosts[i]
        shutdownApp(host)
    }

}

// FUNCTIONS

/**
 * Deploy the app to the local Tomcat server identified by the given "catalinaBase"
 *
 * @param war path to the war file to deploy
 * @param catalinaBase path to the catalina base
 * @param httpPort listen port of the tomcat server
 */
def deployApp(war, catalinaBase, httpPort) {
    shutdownApp(catalinaBase)
    sh """
        export CATALINA_BASE=${catalinaBase}
        rm -rf ${catalinaBase}/webapps/ROOT
        rm -rf ${catalinaBase}/webapps/ROOT.war
        cp -rf ${war} ${catalinaBase}/webapps/ROOT.war
        ${catalinaHome}/bin/startup.sh
    """
    echo "INFO - $catalinaBase server restarted with new webapp $war, see http://localhost:$httpPort"
    retry(count: 5) { sh "sleep 5 && curl http://localhost:$httpPort/health-check.jsp" }
}

/**
 * Shutdown the local Tomcat server identified by the given "catalinaBase"
 *
 * @param catalinaBase path to the catalina base
 */
def shutdownApp(catalinaBase) {
    env.CATALINA_BASE="${catalinaBase}"
    sh """
        export CATALINA_BASE=${catalinaBase}
        ${catalinaHome}/bin/shutdown.sh || :
    """ // use "|| :" to ignore exception if server is not started
    echo "INFO - $catalinaBase server is stopped"
}

/**
 * Deploy Maven on the slave if needed and add it to the path
 */
def ensureMaven() {
    env.PATH = "${tool 'Maven 3.x'}/bin:${env.PATH}"
}
