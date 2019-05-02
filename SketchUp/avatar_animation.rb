# Created by Weijia Zhao in Feb. 11, 2019

require 'sketchup.rb'
require 'extensions.rb'

module ISUSmartHomeLab
  module SmartHomeTool

    unless file_loaded?(__FILE__)
      ex = SketchupExtension.new('Avatar Animation', 'avatar_animation/main')
      ex.description = 'ISU Smart Home Lab Animating an Avatar\'s Activities.'
      ex.version     = '1.0'
      ex.copyright   = 'ISU Smart Home Lab © 2019'
      ex.creator     = 'Weijia Zhao'
      Sketchup.register_extension(ex, true)
      file_loaded(__FILE__)
    end

  end # module SmartHomeTool
end # module ISUSmartHomeLab