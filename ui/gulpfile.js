const inject = require('gulp-inject');
const gulp = require('gulp');
const base64 = require('gulp-base64-inline');
const replace = require('gulp-string-replace');

function replaceUrlForInlineSelector() {
  return gulp.src('./dist/ui/*.css')
    .pipe(replace(/background:url/g, function () {
      return 'background:inline';
    }))
    .pipe(gulp.dest('./dist/ui'))
}

function cssImagesInline() {
  return gulp.src('./dist/ui/*.css')
    .pipe(base64('./'))
    .pipe(gulp.dest('./dist/ui'));
}

function jsCssInline() {
  const target = gulp.src('./template/ui.html');
  const sources = gulp.src(['./dist/ui/**/*.js', './dist/ui/**/*.css']);
  return target.pipe(inject(sources, {
    removeTags: true,
    transform: function (filePath, file) {
      return file.contents.toString('utf8');
    }
  })).pipe(gulp.dest('./dist'));
}

gulp.task('default', replaceUrlForInlineSelector);
gulp.task('cssImagesInline', cssImagesInline);
gulp.task('jsCssInline', jsCssInline);

(gulp.series('default', 'cssImagesInline', 'jsCssInline')());
