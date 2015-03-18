package com.gu.teamcity

import java.io.IOException
import java.util.Date

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.messages.{BuildMessage1, DefaultMessagesInfo, Status}
import jetbrains.buildServer.serverSide.{BuildServerAdapter, BuildServerListener, SRunningBuild}
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts.BuildArtifactsProcessor
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts.BuildArtifactsProcessor.Continuation
import jetbrains.buildServer.serverSide.artifacts.{BuildArtifact, BuildArtifactsViewMode}
import jetbrains.buildServer.util.EventDispatcher
import org.jetbrains.annotations.NotNull

class S3BuildServerListener(eventDispatcher: EventDispatcher[BuildServerListener], extension: S3Extension) extends BuildServerAdapter {
  val client: AmazonS3Client = new AmazonS3Client

  eventDispatcher.addListener(this)

  override def beforeBuildFinish(runningBuild: SRunningBuild) {
    runningBuild.addBuildMessage(normalMessage("About to upload artifacts"))

    for (bucket <- extension.bucketName) {
      if (runningBuild.isArtifactsExists) {
        val uploadDirectory = s"${runningBuild.getProjectExternalId}/${runningBuild.getBuildTypeName}/${runningBuild.getBuildNumber}"
        normalMessage(s"Uploading to: $uploadDirectory")

        runningBuild.getArtifacts(BuildArtifactsViewMode.VIEW_DEFAULT).iterateArtifacts(new BuildArtifactsProcessor {
          def processBuildArtifact(buildArtifact: BuildArtifact) = {
            if (buildArtifact.isFile || buildArtifact.isArchive) {
              try {
                client.putObject(bucket, s"$uploadDirectory/${buildArtifact.getName}", buildArtifact.getInputStream, new ObjectMetadata)
                Continuation.CONTINUE
              }
              catch {
                case e: IOException => {
                  runningBuild.addBuildMessage(new BuildMessage1(DefaultMessagesInfo.SOURCE_ID, DefaultMessagesInfo.MSG_BUILD_FAILURE, Status.ERROR, new Date, s"Error uploading artifacts: ${e.getMessage}"))
                  e.printStackTrace
                  Continuation.BREAK
                }
              }
            } else Continuation.CONTINUE
          }
        })
      }
    }
    runningBuild.addBuildMessage(normalMessage("Artifact S3 upload complete"))
  }

  private def normalMessage(text: String) =
    new BuildMessage1(DefaultMessagesInfo.SOURCE_ID, DefaultMessagesInfo.MSG_TEXT, Status.NORMAL, new Date, text)
}