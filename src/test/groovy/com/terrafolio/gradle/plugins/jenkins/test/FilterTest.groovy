package com.terrafolio.gradle.plugins.jenkins.test;

import static org.junit.Assert.*

import org.gradle.api.Project
import org.junit.Test
import org.junit.Before
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.tasks.TaskExecutionException

import com.terrafolio.gradle.plugins.jenkins.JenkinsPlugin
import com.terrafolio.gradle.plugins.jenkins.JenkinsRESTServiceImpl
import com.terrafolio.gradle.plugins.jenkins.JenkinsServiceException
import com.terrafolio.gradle.plugins.jenkins.JenkinsConfigurationException
import com.terrafolio.gradle.plugins.jenkins.ConsoleFactory

import groovy.mock.interceptor.MockFor

class FilterTest {
	def private final Project project = ProjectBuilder.builder().withProjectDir(new File('build/tmp/test')).build()
	def private final JenkinsPlugin plugin = new JenkinsPlugin()
	def MockFor mockJenkinsRESTService
	
	@Before
	def void setupProject() {
		plugin.apply(project)
		
		project.ext.branches = [
			master: [ parents: [ ] ],
			develop: [ parents: [ 'master' ] ]
		]
		
		project.jenkins {
			server('test1'){
					url 'test1'
					username 'test1'
					password 'test1'
				}
			server('test2'){
					url 'test2'
					username 'test2'
					password 'test2'
				}
			project.branches.eachWithIndex { branchName, map, index ->
				job("compile_${branchName}") {
					server servers.test1 
				}
			}
		}
		
		mockJenkinsRESTService = new MockFor(JenkinsRESTServiceImpl.class)
	}
	
	@Test
	def void execute_filtersServers() {
		mockJenkinsRESTService.demand.with {
			updateJobConfiguration(0) { String jobName, String configXML -> }
			
			2.times {
				getJobConfiguration() { String jobName, Map overrides ->
					null
				}
				
				createJob() { String jobName, String configXML, Map overrides ->
					if (! project.jenkins.jobs.collect { it.name }.contains(jobName)) {
						throw new Exception('createJob called with: ' + jobName + ' but no job definition exists with that name!')
					}
				}
			
			}
		}
		
		project.jenkinsServerFilter = 'test1'
		project.jenkins.jobs.each { job ->
			job.server project.jenkins.servers.test2
		}
		
		mockJenkinsRESTService.use {
			assert [ "test1" ] == project.tasks.updateJenkinsJobs.getServerDefinitions(project.jenkins.jobs."compile_master").collect { it.name }
			project.tasks.updateJenkinsJobs.execute()
		}
	}
	
	@Test
	def void execute_filtersJobs() {
		mockJenkinsRESTService.demand.with {
			updateJobConfiguration(0) { String jobName, String configXML, Map Overrides -> }
			
			2.times {
				getJobConfiguration() { String jobName, Map overrides ->
					null
				}
				
				createJob() { String jobName, String configXML, Map overrides ->
					assert jobName =~ /master/
					if (! project.jenkins.jobs.collect { it.name }.contains(jobName)) {
						throw new Exception('createJob called with: ' + jobName + ' but no job definition exists with that name!')
					}
				}
			
			}
		}
		
		project.jenkinsJobFilter = '.*_master'
		project.jenkins.jobs.each { job ->
			job.server project.jenkins.servers.test2
		}
		
		mockJenkinsRESTService.use {
			project.tasks.updateJenkinsJobs.execute()
		}
	} 
}
