'use strict';

angular.module('jhipsterApp')
    .directive('activeMenu', function($translate, $locale, tmhDynamicLocale) {
        return {
            restrict: 'A',
            link: function(scope, element, attrs, controller) {
                var language = attrs.activeMenu;

                scope.$watch(function() {
                    return $translate.use();
                }, function(selectedLanguage) {
                    if (language === selectedLanguage) {
                        tmhDynamicLocale.set(language);
                        element.addClass('active');
                    } else {
                        element.removeClass('active');
                    }
                });
            }
        };
    })
    .directive('activeLink', function(location) {
        return {
            restrict: 'A',
            link: function(scope, element, attrs, controller) {
                var clazz = attrs.activeLink;
                var path = attrs.href;
                path = path.substring(1); //hack because path does bot return including hashbang
                scope.location = location;
                scope.$watch('location.path()', function(newPath) {
                    if (path === newPath) {
                        element.addClass(clazz);
                    } else {
                        element.removeClass(clazz);
                    }
                });
            }
        };
    }).directive('passwordStrengthBar', function() {
        return {
            replace: true,
            restrict: 'E',
            template: '<div id="strength">' +
                      '<small translate="global.messages.validate.newpassword.strength">Password strength:</small>' +
                      '<ul id="strengthBar">' +
                        '<li class="point"></li><li class="point"></li><li class="point"></li><li class="point"></li><li class="point"></li>' +
                      '</ul>' +
                    '</div>',
            link: function(scope, iElement, attr) {
                var strength = {
                    colors: ['#F00', '#F90', '#FF0', '#9F0', '#0F0'],
                    mesureStrength: function (p) {

                        var _force = 0;
                        var _regex = /[$-/:-?{-~!"^_`\[\]]/g; // "

                        var _lowerLetters = /[a-z]+/.test(p);
                        var _upperLetters = /[A-Z]+/.test(p);
                        var _numbers = /[0-9]+/.test(p);
                        var _symbols = _regex.test(p);

                        var _flags = [_lowerLetters, _upperLetters, _numbers, _symbols];
                        var _passedMatches = $.grep(_flags, function (el) { return el === true; }).length;

                        _force += 2 * p.length + ((p.length >= 10) ? 1 : 0);
                        _force += _passedMatches * 10;

                        // penality (short password)
                        _force = (p.length <= 6) ? Math.min(_force, 10) : _force;

                        // penality (poor variety of characters)
                        _force = (_passedMatches == 1) ? Math.min(_force, 10) : _force;
                        _force = (_passedMatches == 2) ? Math.min(_force, 20) : _force;
                        _force = (_passedMatches == 3) ? Math.min(_force, 40) : _force;

                        return _force;

                    },
                    getColor: function (s) {

                        var idx = 0;
                        if (s <= 10) { idx = 0; }
                        else if (s <= 20) { idx = 1; }
                        else if (s <= 30) { idx = 2; }
                        else if (s <= 40) { idx = 3; }
                        else { idx = 4; }

                        return { idx: idx + 1, col: this.colors[idx] };
                    }
                };
                scope.$watch(attr.passwordToCheck, function(password) {
                    if (password) {
                        var c = strength.getColor(strength.mesureStrength(password));
                        iElement.removeClass('ng-hide');
                        iElement.find('ul').children('li')
                            .css({ "background": "#DDD" })
                            .slice(0, c.idx)
                            .css({ "background": c.col });
                    }
                });
            }
        }
    })
    .directive('showValidation', function() {
        return {
            restrict: "A",
            require:'form',
            link: function(scope, element, attrs, formCtrl) {
                element.find('.form-group').each(function() {
                    var $formGroup=$(this);
                    var $inputs = $formGroup.find('input[ng-model],textarea[ng-model],select[ng-model]');

                    if ($inputs.length > 0) {
                        $inputs.each(function() {
                            var $input=$(this);
                            scope.$watch(function() {
                                return $input.hasClass('ng-invalid') && $input.hasClass('ng-dirty');
                            }, function(isInvalid) {
                                $formGroup.toggleClass('has-error', isInvalid);
                            });
                        });
                    }
                });
            }
        };
    })
    .directive('jhSocial', function() {
        // these link to functionality provided by spring-social
        var authLinks = {
            'facebook': '/auth/facebook?scope=public_profile,email',
            'google-plus': '/auth/google?scope=profile https://www.googleapis.com/auth/plus.profile.emails.read'
        };
        return {
            restrict: 'E',
            replace: true,
            transclude: true,
            link: function(scope, element, attrs, ctrl, transclude) {
                scope.$watch(attrs['externalAccountProvider'],
                    function(externalAccountProvider) {
                        if (name !== undefined) {
                            // build either a block style or icon style button
                            var type = attrs['type'];
                            if (type === 'block') {
                                element.addClass('btn-block btn-social');
                            }
                            else if (type === 'icon') {
                                element.addClass('btn-social-icon');
                            }

                            // turn the enumerated value into something more javascript/css friendly
                            var clientSideProviderName = externalAccountProvider.toLowerCase();
                            if (clientSideProviderName === "google") {
                                // workaround for google
                                clientSideProviderName = "google-plus";
                            }

                            // add classes common to both block and button styles.  for example, "btn-facebook" and "fa-facebook".
                            element.addClass("btn-" + clientSideProviderName);
                            element.find('i').addClass("fa-" + clientSideProviderName);

                            // if the caller wants to create a link to authenticate with the service
                            if ('createAuthLink' in attrs) {
                                element.attr('href', authLinks[clientSideProviderName]);
                            }

                            // include whatever content the caller included in our element
                            element.append(transclude());
                        }
                    }
                );
            },
            template:
                '<a class="btn">' +
                '<i class="fa"></i>' +
                '</a>'
        }
    });
