def tasks = [:]

tasks["vagrant-1"] = {
  node('docker') {  
    stage concurrency: 1, name: 'vagrant-slave-1'
    sh 'echo $NODE_NAME' 
  }
}
tasks["vagrant-2"] = {
  node('docker-cloud') { 
    stage concurrency: 2, name: 'vagrant-slave-2'
    sh 'echo $NODE_NAME' 
  }
}

parallel tasks
