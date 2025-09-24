const path = require('path');
const webpack = require('webpack');
const { CleanWebpackPlugin } = require('clean-webpack-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const { VueLoaderPlugin } = require('vue-loader');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const { GitRevisionPlugin } = require('git-revision-webpack-plugin');

const gitRevisionPlugin = new GitRevisionPlugin();

const faviconPath = path.resolve(__dirname, 'src/static/img/sd-favicon.png');

const pages = [
    { name: 'main', template: 'main.html', chunks: ['commons', 'main'] },
    {
        name: 'room', template: 'room.html', chunks: ['commons', 'room'],
        // Hack: Preserve for backend replacement
        templateParameters: { ROOM_CONFIG: '<%= ROOM_CONFIG %>' }
    },
    {
        name: 'about',
        template: 'about.html',
        chunks: ['commons', 'about'],
        templateParameters: { VERSION: gitRevisionPlugin.version() }
    }
];

module.exports = {
    entry: {
        main: ['./src/js/main.js'],
        room: ['./src/js/room.js'],
        about: ['./src/js/about.js']
    },
    output: {
        path: path.resolve(__dirname, 'dist'),
        filename: 'static/js/[name].[contenthash].js',
        chunkFilename: 'static/js/[name].[contenthash].js'
    },
    module: {
        rules: [
            {
                test: /\.js$/,
                exclude: /node_modules/,
                use: {
                    loader: 'babel-loader',
                    options: {
                        presets: [
                            [
                                '@babel/preset-env',
                                {
                                    targets: '> 0.25%, not dead'
                                }
                            ]
                        ]
                    }
                }
            },
            {
                test: /\.vue$/,
                loader: 'vue-loader'
            },
            {
                test: /\.scss$/,
                use: [
                    MiniCssExtractPlugin.loader,
                    'css-loader',
                    {
                        loader: 'sass-loader',
                        options: {
                            implementation: require('sass')
                        }
                    }
                ]
            },
            {
                test: /\.(png|svg)$/i,
                type: 'asset/resource',
                generator: {
                    filename: 'static/img/[name].[contenthash][ext]'
                }
            }
        ]
    },
    resolve: {
        extensions: ['.js', '.vue'],
        alias: {
            vue$: 'vue/dist/vue.esm-bundler.js'
        }
    },
    optimization: {
        splitChunks: {
            cacheGroups: {
                commons: {
                    name: 'commons',
                    chunks: 'all',
                    minChunks: 2,
                    enforce: true
                }
            }
        }
    },
    plugins: [
        new CleanWebpackPlugin(),
        gitRevisionPlugin,
        new webpack.DefinePlugin({
            __VUE_OPTIONS_API__: true,
            __VUE_PROD_DEVTOOLS__: false
        }),
        new MiniCssExtractPlugin({
            filename: 'static/css/[name].[contenthash].css'
        }),
        new VueLoaderPlugin(),
        new CopyWebpackPlugin({
            patterns: [
                {
                    from: faviconPath,
                    to: 'static/img/sd-favicon.png'
                }
            ]
        }),
        ...pages.map(page =>
            new HtmlWebpackPlugin({
                filename: `${page.name}.html`,
                template: path.resolve(__dirname, `src/html/${page.template}`),
                chunks: page.chunks,
                inject: 'body',
                scriptLoading: 'blocking',
                ...(page.templateParameters ? { templateParameters: page.templateParameters } : {})
            })
        )
    ]
};
