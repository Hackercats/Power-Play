package org.firstinspires.ftc.teamcode;

import com.acmerobotics.dashboard.FtcDashboard;
import com.acmerobotics.dashboard.config.Config;
import com.acmerobotics.roadrunner.geometry.Pose2d;
import com.acmerobotics.roadrunner.geometry.Vector2d;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.teamcode.drive.DriveConstants;
import org.firstinspires.ftc.teamcode.drive.SampleMecanumDrive;
import org.firstinspires.ftc.teamcode.hardware.AutoScoringMech;
import org.firstinspires.ftc.teamcode.hardware.Camera;
import org.firstinspires.ftc.teamcode.hardware.Lift;
import org.firstinspires.ftc.teamcode.trajectorysequence.TrajectorySequence;
import org.firstinspires.ftc.teamcode.util.AutoToTele;
import org.firstinspires.ftc.teamcode.vision.SignalPipeline;

//this autonomous is meant for if you start on the left side of the field
//regular is the red side of the field, -1 is blue side of the field
@Config
@Autonomous
public class Auto extends LinearOpMode {
    // Pre init
    SampleMecanumDrive drive;
    Camera camera;
    SignalPipeline signalPipeline = new SignalPipeline();
    AutoScoringMech scoringMech;

    int side = 1;
    String sidename;

    Pose2d startPos;
    Pose2d parkPos;
    Pose2d parkPos1;
    Pose2d parkPos2;
    Pose2d parkPos3;
    Pose2d preloadScoringPos;
    TrajectorySequence driveToPreloadPos;
    TrajectorySequence toStack;
    double grabApproachVelo = 15;
    TrajectorySequence toJunction;
    TrajectorySequence park;

    @Override
    public void runOpMode(){
        // Init
        // Bind stuff to the hardwaremap
        drive = new SampleMecanumDrive(hardwareMap);
        scoringMech = new AutoScoringMech(hardwareMap);
        camera = new Camera(hardwareMap, signalPipeline);
        FtcDashboard.getInstance().startCameraStream(camera.webcam, 3);

        ElapsedTime pipelineThrottle = new ElapsedTime(100000);

        // Init loop
        while (!isStarted()&&!isStopRequested()){
            if (gamepad1.circle) side = 1; // Red alliance
            if (gamepad1.cross) side = -1; // Blue alliance
            AutoToTele.allianceSide = side;
            // Is doing an if else statement on one line bad?
            if (side == 1) sidename = "Red Terminal"; else sidename = "Blue Terminal";

            // Recompute trajectories every second
            if (pipelineThrottle.milliseconds() > 1000){

                startPos = new Pose2d(-35.8, -60.6*side, Math.toRadians(-90*side));
                drive.setPoseEstimate(startPos);

                parkPos1 = new Pose2d(-58.5, -32*side, Math.toRadians(-90*side));
                parkPos2 = new Pose2d(-36, -32*side, Math.toRadians(-90*side));
                parkPos3 = new Pose2d(-11, -32*side, Math.toRadians(-90*side));

                switch(signalPipeline.getParkPos()){
                    case 1:
                        // Switch 1 and 3 if we're on blue terminal
                        if (side == 1){
                            parkPos = parkPos1;
                        } else {
                            parkPos = parkPos3;
                        }
                        break;
                    case 2:
                        // We don't have to change the middle positon ever
                        parkPos = parkPos2;
                        break;
                    case 3:
                        // Switch 3 and 1 if we're on blue terminal
                        if (side == 1) {
                            parkPos = parkPos3;
                        } else {
                            parkPos = parkPos1;
                        }
                        break;
                }

                // Update trajectories
                preloadScoringPos = new Pose2d(-12, -39.5*side, Math.toRadians(-126*side));

                driveToPreloadPos = drive.trajectorySequenceBuilder(startPos)
                        .back(2.5)
                        .lineToSplineHeading(new Pose2d(-10, -56*side,Math.toRadians(-90*side)))
                        .lineToSplineHeading(preloadScoringPos)
                        .build();

                toStack = drive.trajectorySequenceBuilder(driveToPreloadPos.end())
                        .setTangent(Math.toRadians(-126))
                        .splineToSplineHeading(new Pose2d(-40,-12*side, Math.toRadians(180)), Math.toRadians(180))
                        // Slow the bot down for the cone grab
                        .setVelConstraint(SampleMecanumDrive.getVelocityConstraint(grabApproachVelo, DriveConstants.MAX_ANG_VEL, DriveConstants.TRACK_WIDTH))
                        .lineTo(new Vector2d(-60, -12*side))
                        .build();

                park = drive.trajectorySequenceBuilder(driveToPreloadPos.end())
                        .lineToSplineHeading(parkPos)
                        .build();

                // Display what park zone the robot is planning on going to and other things in telemetry
                telemetry.addLine(sidename);
                telemetry.addData("alliance", AutoToTele.allianceSide);
                telemetry.addData("park zone", signalPipeline.getParkPos());
                telemetry.addLine("CHECK THE AUTO, REMEMBER NANO FINALS 3!");
                telemetry.update();
                pipelineThrottle.reset();
            } // End of throttled section
        }

        waitForStart();
        camera.stopStreaming();
        // Start of actual auto instructions
        if (opModeIsActive()){
            // Auto code
            scoringMech.grab(); // Grip the preload
            sleep(200);
            // Move the v4b vertical to save time when scoring and stop the cone from dragging on the ground
            scoringMech.preMoveV4B();

            drive.followTrajectorySequence(driveToPreloadPos);

            scoringMech.score(Lift.highPos);

            drive.followTrajectorySequence(toStack);

            drive.followTrajectorySequence(park);

            // Save this stuff at the end to calibrate feild centric automatically
            AutoToTele.endOfAutoPose = drive.getPoseEstimate();
            AutoToTele.endOfAutoHeading = drive.getExternalHeading();
        }
    }
}

//luke was here
// And Ely