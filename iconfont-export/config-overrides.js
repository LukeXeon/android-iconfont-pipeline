const paths = require('react-scripts/config/paths');
const path = require('path');
module.exports = function override(config, env) {
    paths.appBuild = path.join(paths.appBuild, 'app');
    if (config.output.path){
        config.output.path = path.join(config.output.path, 'app');
    }
    return config;
};