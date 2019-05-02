# Created by Weijia Zhao in Feb. 11, 2019

require 'sketchup.rb'
require 'socket'

SKETCHUP_CONSOLE.show

module ISUSmartHomeLab
  module AvatarAnimation

    class Avatar

      def activate
        @@model = Sketchup.active_model
        @@view = @@model.active_view
        @@entities = @@model.entities

        @@entities.each do |obj|
          if obj.class == Sketchup::ComponentInstance
            if obj.name == "Avatar"
              @@componentInstance = obj
              break
              # selection = @@model.selection
              # selection.add(@@componentInstance)
            end
          end
        end
        
        @@transformation = @@componentInstance.transformation
        @@avatarPosition = @@componentInstance.bounds.center


        # The bounding box in Sketchup is NOT the same as the visual box we see in the viewport
        # bb = @componentInstance.bounds
        # selection = @model.selection
        # selection.add(@componentInstance)
        
        # pos = Geom::Point3d.new(265,164,0)
        # moveToPosition(pos)

        resetModel
        setBuildingBoundary
        resetCamera
        getViewInfo

      end

      def deactivate(view)
        # puts "Animation Tool is deactivated"
        view.invalidate
      end

      def resume(view)
        view.invalidate
      end

      def resetModel
        origin = Geom::Point3d.new(245, 163, 0)
        # updateCurrentPosition(origin)

        newTransformation = Geom::Transformation.new(origin)
        @@componentInstance.move! newTransformation
        updateAvatarLocation
        updateTransformation
        updateViewPort

      end

      def resetCamera
        dir = getCurrentDirection
        @@eye = updateCameraEyePositionInRoomView(dir)
        @@target = updateAvatarLocation
        @@up = [0, 0, 1]

        # @@fixedAeralUp = Geom::Vector3d.new(0.005389, -0.999496, 0.03129)
        @@fixedAeralUp = Geom::Vector3d.new(0, -1, 0)

        @@aerialView = false
        @@eyeView = false
        @@roomView = true
        @@viewChanged = false

        camera = Sketchup::Camera.new @@eye, @@target, @@up
        @@view.camera = camera

        @@stepDistance = 0
        @@rotateAngle = 0
        @@moveUp = false
        @@moveDown = false
        @@rotateLeft = false
        @@rotateRight = false
        @@moveRemote = false
        @@rotationArray = Array.new
      end

      def getCurrentDirection
        # The bounding box in Sketchup is NOT the same as the visual box we see in the viewport
        # bb = @componentInstance.bounds
        # corner1 = bb.corner(2)
        # corner2 = bb.corner(0)
        # vector = corner2.vector_to(corner1)
        # vector
        angle = (getActualAngle + 270) % 360
        x_axis, y_axis, z_axis = 0, 0, 0

        # Compute the vector between x axis given the angle between them
        if(angle == 0)
          x_axis = 1
          y_axis = 0
        elsif(angle > 0 && angle < 90)
          x_axis = 1
          # Need to convert degrees to raidans, since the parameter in Math::tan() is in radian unit.
          y_axis = Math::tan(angle.degrees)
        elsif(angle == 90)
          x_axis = 0
          y_axis = 1
        elsif(angle > 90 && angle < 180)
          x_axis = -1
          y_axis = Math::tan((180 - angle).degrees)
        elsif(angle == 180)
          x_axis = -1
          y_axis = 0
        elsif(angle > 180 && angle < 270)
          x_axis = -1
          y_axis = -Math::tan((angle - 180).degrees)
        elsif(angle == 270)
          x_axis = 0
          y_axis = -1
        elsif(angle > 270 && angle < 360)
          x_axis = 1
          y_axis = -Math::tan((360 - angle).degrees)
        end

        point1 = Geom::Point3d.new(0, 0, 0)
        point2 = Geom::Point3d.new(x_axis, y_axis, z_axis)

        vector = point1.vector_to(point2)
        vector
        
      end

      # rotate the model to degree
      def rotateToDegree(degree)
        preDegree = getActualAngle
        adjustment = degree - preDegree
        rotateByDegree(adjustment)
      
        dir = getCurrentDirection
        if(@@roomView)
          @@rotationArray.unshift(dir) 
        end

        if(@@eyeView) 
          @@rotateAngle = adjustment
          @@view.animation = Avatar.new
          @@rotateAngle = 0
        end
      end


      def getActualAngle
        # Get the model rotation around the z axis, measured in degree
        rotz = @@componentInstance.transformation.rotz
        angle = 0
        if(rotz >= 0 && rotz <= 180)
          angle = rotz
        elsif(rotz >= -180 && rotz < 0)
          angle = 360 + rotz
        end
        # puts "in getActualAngle: angle = #{angle}"
        angle
      end
      

      def getCurrentPosition
        position = @@componentInstance.transformation.origin
        position
      end

      def updateCurrentPosition(position)
        return unless defined? position
        return unless (position.class == Geom::Point3d || position.size == 3)
        @@ptx = position[0]
        @@pty = position[1]
        @@ptz = position[2]
      end

      def updateAvatarLocation
        @@avatarPosition = @@componentInstance.bounds.center
      end


      def updateTransformation
        @@transformation = @@componentInstance.transformation
      end

      def moveToPosition(position)
        # do nothing if the position is invalid, it must be a Point3d object or an array with 3 elements
        return unless defined? position
        return unless (position.class == Geom::Point3d || position.size == 3)

        # updateCurrentPosition(position)
        # transformation is a 4 x 4 matrix, it stores rotation, position, scaling informations
        arr = @@transformation.to_a
        # index 12 stores the current x axis information
        arr[12] = position[0]
        # index 13 stores the current y axis information
        arr[13] = position[1]
        # index 14 stores the current z axis information
        arr[14] = position[2]

        
        newTransformation = Geom::Transformation.new(arr);
        @@componentInstance.move! newTransformation

        updateTransformation
        updateAvatarLocation
        updateViewPort
        @@stepDistance = 20
        @@view.animation = Avatar.new

        @@stepDistance = 0

        puts "#{@@avatarPosition}"

      end

      def moveByDistanceAndUpdateCamera(distance)
        vector = getCurrentDirection
        vector.length = distance
        @@stepDistance = distance
        moveTransformation = Geom::Transformation.translation(vector)
        @@componentInstance.transform! moveTransformation
        updateAvatarLocation
        updateTransformation
        updateViewPort

        @@view.animation = Avatar.new
        @@stepDistance = 0
      end

      # Move the model up or front along its own facing direction
      # If dis is positive, then move up, else, move down
      def moveUpAndDown(dis)
        vector = getCurrentDirection
        vector.length = dis
        moveTransformation = Geom::Transformation.translation(vector)
        @@componentInstance.transform! moveTransformation
        updateAvatarLocation
        updateTransformation
        updateViewPort

        

      end

      # Rotation about the model
      def getCurrentRotation
        rotx = @@componentInstance.transformation.rotx
        roty = @@componentInstance.transformation.roty
        rotz = @@componentInstance.transformation.rotz

        rotation = []
        rotation << rotx
        rotation << roty
        rotation << rotz
        rotation
      end

      # Rotate the model by angle, measured by degree
      # If angle is positive, then turn LEFT, otherwise, turn RIGHT
      def rotateByDegree(angle)
        return unless defined? angle
        currentPosition = getCurrentPosition
        z_vector = [0, 0, 1]

        rotationTransformation = Geom::Transformation.rotation(currentPosition, z_vector, angle.degrees)
        @@componentInstance.transform! (rotationTransformation)

        # Need to update the most recent transformation!!!
        # Without updating, the rotation will be changed to the initial status when move the model.
        updateAvatarLocation
        updateTransformation
        updateViewPort

      end

      def onKeyDown(key, repeat, flags, view)
        getViewInfo
        case key
          when VK_UP
            # moveUpAndDown(@@stepDistance)
            @@moveUp = true
            @@stepDistance = 20

          when VK_DOWN
            # moveUpAndDown(-@@stepDistance)
            @@moveDown = true
            @@stepDistance = -20

          when VK_LEFT
            @@rotateAngle = 20
            # rotateByDegree(@@rotateAngle)
            @@rotateLeft = true

          when VK_RIGHT
            @@rotateAngle = -20
            # rotateByDegree(@@rotateAngle)
            @@rotateRight = true

          # key "1" is pressed, show aerial view
          when 49 
            @@aerialView = true
            @@eyeView = false
            @@roomView = false
            changeCameraView(true, false, false)

          # key '2' is pressed, show room vivew
          when 50
            @@aerialView = false
            @@eyeView = false
            @@roomView = true
            changeCameraView(false, false, true)

          # key '3' is pressed, show eye vivew
          when 51
            @@aerialView = false
            @@eyeView = true
            @@roomView = false
            changeCameraView(false, true, false)

        end

        @@view.animation = Avatar.new

      end

      def onKeyUp(key, repeat, flags, view)

        case key
          when VK_UP
            @@moveUp = false

          when VK_DOWN
            @@moveDown = false

          when VK_LEFT
            @@rotateLeft = false

          when VK_RIGHT
            @@rotateRight = false

        end

        if (@@moveUp == false and @@moveDown == false)
          @@stepDistance = 0
        end

        if (@@rotateLeft == false and @@rotateRight == false)
          @@rotateAngle = 0
        end

      end


      def getViewInfo
        camera = @@view.camera
        @@target = camera.target
        @@eye = camera.eye
  
      end

      def setBuildingBoundary
        @@left = -64.3 * 12
        @@right = 85.6 * 12
        @@top = 18.7 * 12
        @@bottom = -56.9 * 12
      end

      def isEyeOutOfBoundary(eyePos)
        if(eyePos.x <= @@left or eyePos.x >= @@right or eyePos.y <= @@bottom or eyePos.y >= @@top)
          return true
        end
        return false
      end

      def updateCameraEyePositionInRoomView(dir)
        temDir = dir.reverse.clone
        temDir.length = 200
        eye = @@avatarPosition.offset(temDir)
        eye.z = 33 * 12

        if(isEyeOutOfBoundary(eye))
          temDir = dir.clone
          temDir.length = 230
          eye = @@avatarPosition.offset(temDir)
          eye.z = 33 * 12
        end
        eye

      end

      def changeCameraView(aerial, eye, room)
        if(aerial)
          @@eyeView = false
          @@aerialView = true
          @@roomView = false
          @@rotationArray = Array.new
          pos = Geom::Point3d.new(@@avatarPosition)
          pos.z = 600
          @@eye = Geom::Point3d.new(pos)
          @@target = Geom::Point3d.new(@@avatarPosition)
          @@up = Geom::Vector3d.new(@@fixedAeralUp)

        end

        if(eye)
          @@eyeView = true
          @@aerialView = false
          @@roomView = false
          @@rotationArray = Array.new
          dir = getCurrentDirection.reverse
          pos = updateAvatarLocation

          dir.length = 150
          @@eye = pos.offset(dir)
          @@eye.z = 70
        
          dir = getCurrentDirection
          dir.length = 300
          @@target = pos.offset(dir)
          @@target.z = 40
          @@up = [0, 0, 1]

        end

        if(room)
          @@eyeView = false
          @@aerialView = false
          @@roomView = true
          dir = getCurrentDirection
          @@eye = updateCameraEyePositionInRoomView(dir)
          @@target = updateAvatarLocation
          @@up = [0, 0, 1]
        end

        @@view.camera.set(@@eye, @@target, @@up)
        @@view.show_frame
        self.getViewInfo
      end

      def nextFrame(view)

        if @@stepDistance == 0 and @@rotateAngle == 0
          return false
        end
        
        if(@@roomView)

          if(@@rotateLeft or @@rotateRight)
            rotateByDegree(@@rotateAngle)
            dir = getCurrentDirection
            @@rotationArray.unshift(dir)
            return false
          end
          
          if(@@moveUp or @@moveDown)
            moveUpAndDown(@@stepDistance)
          end

          if(@@rotationArray.size != 0)
            dir = @@rotationArray.pop
            @@target = updateAvatarLocation
            @@eye = updateCameraEyePositionInRoomView dir
            @@view.camera.set(@@eye, @@target, @@up)
            view.show_frame
            self.getViewInfo
            return true
          end


          @@target = updateAvatarLocation
          dir = getCurrentDirection
          @@eye = updateCameraEyePositionInRoomView dir
          # @@model.active_entities.add_line(@@eye, @@target)
          
        elsif(@@eyeView)
          if(@@moveUp or @@moveDown)
            moveUpAndDown(@@stepDistance)
          end

          if(@@rotateLeft or @@rotateRight)
            rotateByDegree(@@rotateAngle)
          end

          dir = getCurrentDirection.reverse
          pos = updateAvatarLocation

          dir.length = 150
          @@eye = pos.offset(dir)
          @@eye.z = 70
        
          dir = getCurrentDirection
          dir.length = 300
          @@target = pos.offset(dir)
          @@target.z = 40
          @@up = [0, 0, 1]

        elsif(@@aerialView)

          if(@@moveUp or @@moveDown)
            moveUpAndDown(@@stepDistance)
          end

          if(@@rotateLeft or @@rotateRight)
            rotateByDegree(@@rotateAngle)
            return true
          end
          pos = Geom::Point3d.new(@@avatarPosition)
          pos.z = 600
          @@eye = Geom::Point3d.new(pos)
          @@target = Geom::Point3d.new(@@avatarPosition)
          @@up = Geom::Vector3d.new(@@fixedAeralUp)
          # @@target = Geom::Point3d.new(0, 0, 0)
          # @@target = updateAvatarLocation

        end
        
        @@view.camera.set(@@eye, @@target, @@up)
        view.show_frame
        self.getViewInfo
        return true

      end
      

      def updateViewPort
        @@model.active_view.refresh
      end


      def generateNextPosition
        x = rand(-20..20)
        y = rand(-20..20)
        z = rand(-20..20)
        position = []
        position << x
        position << y
        position << z
        position

      end

    end # class Avatar


    # This class will contruct a TCP connection with the remote control
    # It takes care of remote commands and responses
    class RemoteListener

      def initialize(url, portNo)
        @remoteURL = url
        @port = portNo
        @connected = false
        @data = ""
        @socket = ""
        connectToServer
      end

      def connectToServer
        begin
          @socket = TCPSocket.new(@remoteURL, @port)
          puts "Connected"
        rescue
          puts "Connot establish connection..."
          return
        end

        @connected = true
        @avatarModel = Avatar.new
        Sketchup.active_model.select_tool(@avatarModel)

        timer = UI.start_timer(0.02, true) {

          begin
            rawdata = dataReceiver.chomp
            # puts "raw Data received: #{rawdata}"
            if !rawdata.nil?
              data = rawdata.split("\n")
              # puts "data #{data}"
              if !data.nil?
                data.each do |item|
                  if item.include? "degree:"
                    puts "Orientation received"
                    degree = item[7..-1].to_f
                    @avatarModel.rotateToDegree(degree)
                  end

                  if item.include? "location:"
                    puts "Avatar indoor location received"
                    locationString = item[9..-1]
                    location = locationString.split(",")
                    position = Geom::Point3d.new(location[0].to_f, location[1].to_f, location[2].to_f)
                    dis = position.distance(@avatarModel.getCurrentPosition)
                    if(dis <= 20 * 20)
                      @avatarModel.moveToPosition(position)
                    end
                  end

                  if item.include? "distance:"
                    puts "Movement received"
                    distance = item[9..-1].to_f
                    @avatarModel.moveByDistanceAndUpdateCamera(distance)
                  end
                end
              end
            end


          rescue IO::EWOULDBLOCKWaitReadable
          rescue Errno::ECONNRESET
          rescue EOFError
            UI.stop_timer timer
            UI.messagebox("Lost connection! \nPlease check your network.")
            close
          end
        }
      end

      def dataReceiver
        return unless isConnected?
        @data = @socket.read_nonblock(1000)
        @data
      end

      def close
        @connected = false
        @socket.close
      end

      def isConnected?
        return @connected
      end

    end # class RemoteListener


    def self.activate_avatar_tool
      Sketchup.active_model.select_tool(Avatar.new)
    end

    def self.startListening
      listener = RemoteListener.new("10.26.52.91", 8888)
      # Sketchup.active_model.select_tool(listener)
      listener
    end

    unless file_loaded?(__FILE__)
      menu = UI.menu('Plugins')
      menu.add_item('Start Avatar Animation') {

        # self.activate_avatar_tool

        listener = self.startListening
        if(listener.isConnected?)
          puts "Animation Tool is activated"
          self.activate_avatar_tool
        end

      }

      file_loaded(__FILE__)
    end

  end # module AvatarAnimation
end # module ISUSmartHomeLab
