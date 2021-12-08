const iconfont = require('gulp-iconfont');
const gulp = require('gulp');
const fs = require('fs');
const del = require('del');
const path = require('path');
const argv = require('minimist')(process.argv.slice(2));
const input = argv['input'] || "assets/icons";
const output = argv['output'] || 'build/';
const fontName = argv['font-name'] || 'iconfont';
const version = argv['version'] || '0.0.1';
const outputWithVersion = path.join(output, version);

/* Creates a unicode literal based on the string */
function unicodeLiteral(str) {
    function fixedHex(number, length) {
        let str = number.toString(16).toUpperCase();
        while (str.length < length)
            str = "0" + str;
        return str;
    }

    let i;
    let result = "";
    for (i = 0; i < str.length; ++i) {
        /* You should probably replace this by an isASCII test */
        if (str.charCodeAt(i) > 126 || str.charCodeAt(i) < 32)
            result += fixedHex(str.charCodeAt(i), 4);
        else
            result += str[i];
    }

    return result.toLocaleLowerCase();
}

function saveJson(list) {
    const icons = {};
    for (let e of list) {
        icons[e.name] = e.unicode
    }
    const json = {icons, version, fonts: ["./" + fontName + ".ttf"]};
    const exportJson = path.join(outputWithVersion, "export.json");
    if (fs.existsSync(exportJson)) {
        fs.unlinkSync(exportJson);
    }
    fs.writeFileSync(exportJson, JSON.stringify(json));
}

gulp.task('clean', async () => {
    await del([
        path.join(output, '/**/*'),
    ]);
});

gulp.task('iconfont', () => {
    return gulp.src([path.join(input, '*.svg')])
        .pipe(iconfont({
            fontName: fontName,
            formats: ['ttf'],
        }))
        .on('glyphs', function (glyphs, options) {
            // CSS templating, e.g.
            saveJson(glyphs.map((e) => {
                return {
                    name: e.name,
                    unicode: unicodeLiteral(e.unicode[0])
                }
            }));
            //console.log(glyphs, options);
        })
        .pipe(gulp.dest(outputWithVersion));
});

gulp.task("default", gulp.series("clean", "iconfont"));
